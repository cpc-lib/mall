package cc.ivera.product.application.impl;

import cc.ivera.product.application.InventoryService;
import cc.ivera.product.domain.enums.InventoryBizType;
import cc.ivera.product.domain.enums.ReservationStatus;
import cc.ivera.product.domain.model.*;
import cc.ivera.product.domain.repository.InventoryReservationRepository;
import cc.ivera.product.domain.repository.InventoryTransactionRepository;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存域服务实现。订单明细与退款明细均由调用方以跨服务快照传入，
 * 库存域不反查订单，也不写订单表。
 */
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryTransactionRepository transactionRepository;

    private final MeterRegistry meterRegistry;

    public InventoryServiceImpl(ProductRepository productRepository,
                                InventoryReservationRepository reservationRepository,
                                InventoryTransactionRepository transactionRepository,
                                MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.transactionRepository = transactionRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserveForOrder(String orderNo, Date expireTime, List<ReserveLine> lines) {
        if (orderNo == null || expireTime == null || lines == null || lines.isEmpty()) {
            throw new BizException("库存预占缺少订单或明细");
        }
        // 按 productId 排序预占，避免并发下单对同一商品集交叉加锁造成死锁。
        List<ReserveLine> sorted = lines.stream()
            .sorted(Comparator.comparing(ReserveLine::getProductId))
            .collect(Collectors.toList());
        for (ReserveLine line : sorted) {
            if (line.getProductId() == null || line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new BizException("库存预占明细数据非法，productId=" + line.getProductId());
            }
            InventoryReservation reservation = new InventoryReservation();
            reservation.setReservationNo(OrderNoUtils.getReservationNo());
            reservation.setOrderNo(orderNo);
            reservation.setOrderItemId(line.getOrderItemId());
            reservation.setProductId(line.getProductId());
            reservation.setQuantity(line.getQuantity());
            reservation.setStatus(ReservationStatus.LOCKED.getType());
            reservation.setExpireTime(expireTime);
            try {
                // 唯一键 uk_reservation_order_product(order_no, product_id) 兼作幂等闸门：已预占则整项跳过。
                reservationRepository.insert(reservation);
            } catch (DuplicateKeyException e) {
                recordReserve("duplicate");
                log.info("库存预占已存在，幂等跳过，orderNo={}, productId={}", orderNo, line.getProductId());
                continue;
            }
            int reserved = productRepository.reserveStock(line.getProductId(), line.getQuantity());
            if (reserved == 0) {
                recordReserve("insufficient");
                // 抛出后整个下单事务回滚（含预占记录），可用库存不变。
                throw new BizException("库存不足，productId=" + line.getProductId());
            }
            insertTransaction(InventoryBizType.ORDER_RESERVE, orderNo, line.getOrderItemId(), null,
                line.getProductId(), -line.getQuantity(), line.getQuantity(), 0);
        }
        recordReserve("success");
    }

    private void recordReserve(String result) {
        Counter.builder("mall.inventory.reserve").tag("result", result).register(meterRegistry).increment();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitReservation(String orderNo) {
        int changed = reservationRepository.casTransition(orderNo,
            ReservationStatus.LOCKED.getType(), ReservationStatus.COMMITTED.getType(), "commitTime");
        List<InventoryReservation> reservations = reservationRepository.listByOrderNo(orderNo);
        if (changed == 0) {
            if (reservations.isEmpty()) {
                log.warn("提交库存预占时未找到预占记录，放行（历史订单），orderNo={}", orderNo);
                return;
            }
            if (reservations.stream().allMatch(r -> ReservationStatus.COMMITTED.getType().equals(r.getStatus()))) {
                log.info("库存预占已全部提交，幂等返回，orderNo={}", orderNo);
                return;
            }
            if (reservations.stream().anyMatch(r -> ReservationStatus.RELEASED.getType().equals(r.getStatus()))) {
                throw new BizException("订单库存预占已释放，无法提交，orderNo=" + orderNo);
            }
            log.warn("库存预占状态不支持提交，幂等返回，orderNo={}", orderNo);
            return;
        }
        // 支付成功仅推进预占状态，库存保持锁定（数量不变），待确认收货时结转已售。
        for (InventoryReservation reservation : reservations) {
            insertTransaction(InventoryBizType.ORDER_COMMIT, orderNo, reservation.getOrderItemId(), null,
                reservation.getProductId(), 0, 0, 0);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void convertToSoldOnReceipt(String orderNo, List<OrderItemStockLine> items) {
        if (items == null || items.isEmpty()) {
            log.warn("确认收货结转未找到订单明细，跳过，orderNo={}", orderNo);
            return;
        }
        for (OrderItemStockLine item : items) {
            int qty = item.getQuantity() - (item.getRestockedQty() == null ? 0 : item.getRestockedQty());
            if (qty <= 0) {
                log.info("确认收货结转数量为0，跳过，orderNo={}, orderItemId={}", orderNo, item.getOrderItemId());
                continue;
            }
            String bizNo = InventoryBizType.ORDER_SOLD.name() + ":" + orderNo + ":" + item.getOrderItemId();
            if (transactionRepository.countByBizNo(bizNo) > 0) {
                log.info("确认收货结转流水已存在，幂等跳过，orderNo={}, orderItemId={}", orderNo, item.getOrderItemId());
                continue;
            }
            int converted = productRepository.commitSoldStock(item.getProductId(), qty);
            if (converted == 0) {
                // 旧模型存量在途单的锁定已在支付时被扣减，无法结转：不阻塞收货，仅告警留痕。
                log.warn("确认收货结转失败（锁定库存不足），跳过，orderNo={}, orderItemId={}, productId={}, qty={}",
                    orderNo, item.getOrderItemId(), item.getProductId(), qty);
                continue;
            }
            insertTransaction(InventoryBizType.ORDER_SOLD, orderNo, item.getOrderItemId(), null,
                item.getProductId(), 0, -qty, qty);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseReservation(String orderNo) {
        int changed = reservationRepository.casTransition(orderNo,
            ReservationStatus.LOCKED.getType(), ReservationStatus.RELEASED.getType(), "releaseTime");
        List<InventoryReservation> reservations = reservationRepository.listByOrderNo(orderNo);
        if (changed == 0) {
            if (reservations.isEmpty()) {
                log.warn("释放库存预占时未找到预占记录，放行（历史订单），orderNo={}", orderNo);
                return;
            }
            if (reservations.stream().allMatch(r -> ReservationStatus.RELEASED.getType().equals(r.getStatus()))) {
                log.info("库存预占已全部释放，幂等返回，orderNo={}", orderNo);
                return;
            }
            if (reservations.stream().anyMatch(r -> ReservationStatus.COMMITTED.getType().equals(r.getStatus()))) {
                throw new BizException("订单已支付成交，库存预占不可释放，orderNo=" + orderNo);
            }
            log.warn("库存预占状态不支持释放，幂等返回，orderNo={}", orderNo);
            return;
        }
        for (InventoryReservation reservation : reservations) {
            int released = productRepository.releaseReservedStock(reservation.getProductId(), reservation.getQuantity());
            if (released == 0) {
                // 预占状态已由 casTransition 转为 RELEASED，库存桶若已被释放（可能是补偿重入或并发关单）
                // 则无需阻塞关单；记录告警便于排查桶与预占状态的瞬时不一致。
                log.warn("库存预占释放时桶 CAS 未命中（locked_stock 可能已释放），orderNo={}, productId={}, qty={}",
                    orderNo, reservation.getProductId(), reservation.getQuantity());
                continue;
            }
            insertTransaction(InventoryBizType.ORDER_RELEASE, orderNo, reservation.getOrderItemId(), null,
                reservation.getProductId(), reservation.getQuantity(), -reservation.getQuantity(), 0);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restockForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt) {
        if (refundNo == null || items == null || items.isEmpty()) {
            return;
        }
        for (RefundStockLine item : items) {
            String bizNo = InventoryBizType.REFUND_RESTOCK.name() + ":" + refundNo + ":" + item.getOrderItemId();
            if (transactionRepository.countByBizNo(bizNo) > 0) {
                log.info("退款回补流水已存在，幂等跳过，refundNo={}, orderItemId={}", refundNo, item.getOrderItemId());
                continue;
            }
            // 来源桶分流：未收货从锁定归还，已收货从已售归还。
            int moved = afterReceipt
                ? productRepository.releaseSoldStock(item.getProductId(), item.getRefundQty())
                : productRepository.releaseReservedStock(item.getProductId(), item.getRefundQty());
            if (moved == 0) {
                throw new BizException(afterReceipt
                    ? "已售库存不足，无法回补，productId=" + item.getProductId()
                    : "库存预占释放失败，productId=" + item.getProductId());
            }
            insertTransaction(InventoryBizType.REFUND_RESTOCK, null, item.getOrderItemId(), refundNo,
                item.getProductId(), item.getRefundQty(),
                afterReceipt ? 0 : -item.getRefundQty(),
                afterReceipt ? -item.getRefundQty() : 0);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeOffLostForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt) {
        if (refundNo == null || items == null || items.isEmpty()) {
            return;
        }
        for (RefundStockLine item : items) {
            String bizNo = InventoryBizType.REFUND_LOST.name() + ":" + refundNo + ":" + item.getOrderItemId();
            if (transactionRepository.countByBizNo(bizNo) > 0) {
                log.info("退款货损核销流水已存在，幂等跳过，refundNo={}, orderItemId={}", refundNo, item.getOrderItemId());
                continue;
            }
            // 来源桶分流：未收货货在锁定桶（locked→lost），已收货货已售出（sold→lost，货留用户不回仓）。
            int written = afterReceipt
                ? productRepository.writeOffSoldLostStock(item.getProductId(), item.getRefundQty())
                : productRepository.writeOffLostStock(item.getProductId(), item.getRefundQty());
            if (written == 0) {
                throw new BizException(afterReceipt
                    ? "已售库存不足，无法核销货损，productId=" + item.getProductId()
                    : "锁定库存不足，无法核销货损，productId=" + item.getProductId());
            }
            insertTransaction(InventoryBizType.REFUND_LOST, null, item.getOrderItemId(), refundNo,
                item.getProductId(), 0,
                afterReceipt ? 0 : -item.getRefundQty(),
                afterReceipt ? -item.getRefundQty() : 0,
                item.getRefundQty());
        }
    }

    private void insertTransaction(InventoryBizType bizType, String orderNo, Long orderItemId, String refundNo,
                                   Long productId, int availableDelta, int lockedDelta, int soldDelta) {
        insertTransaction(bizType, orderNo, orderItemId, refundNo, productId,
            availableDelta, lockedDelta, soldDelta, 0);
    }

    private void insertTransaction(InventoryBizType bizType, String orderNo, Long orderItemId, String refundNo,
                                   Long productId, int availableDelta, int lockedDelta, int soldDelta, int lostDelta) {
        InventoryTransaction transaction = new InventoryTransaction();
        // 预占可能先于落单执行，此时尚无 orderItemId，用 productId 作为同单同商品的流水幂等键。
        Object bizKey = orderItemId != null ? orderItemId : productId;
        transaction.setBizNo(bizType.name() + ":" + (refundNo != null ? refundNo : orderNo) + ":" + bizKey);
        transaction.setBizType(bizType.getType());
        transaction.setOrderNo(orderNo);
        transaction.setOrderItemId(orderItemId);
        transaction.setRefundNo(refundNo);
        transaction.setProductId(productId);
        transaction.setAvailableDelta(availableDelta);
        transaction.setLockedDelta(lockedDelta);
        transaction.setSoldDelta(soldDelta);
        transaction.setLostDelta(lostDelta);
        try {
            transactionRepository.append(transaction);
        } catch (DuplicateKeyException e) {
            // 审计流水唯一键冲突：同一动作已落流水，幂等跳过。
            log.info("库存流水已存在，幂等跳过，bizNo={}", transaction.getBizNo());
        }
    }
}
