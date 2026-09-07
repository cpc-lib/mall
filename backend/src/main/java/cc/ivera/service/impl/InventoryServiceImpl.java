package cc.ivera.service.impl;

import cc.ivera.entity.InventoryReservation;
import cc.ivera.entity.InventoryTransaction;
import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderItem;
import cc.ivera.entity.RefundItem;
import cc.ivera.enums.InventoryBizType;
import cc.ivera.enums.ReservationStatus;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.InventoryReservationMapper;
import cc.ivera.mapper.InventoryTransactionMapper;
import cc.ivera.mapper.OrderItemMapper;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.service.InventoryService;
import cc.ivera.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProductMapper productMapper;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final OrderItemMapper orderItemMapper;

    public InventoryServiceImpl(ProductMapper productMapper,
                                InventoryReservationMapper reservationMapper,
                                InventoryTransactionMapper transactionMapper,
                                OrderItemMapper orderItemMapper) {
        this.productMapper = productMapper;
        this.reservationMapper = reservationMapper;
        this.transactionMapper = transactionMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserveForOrder(OrderInfo order, List<OrderItem> items) {
        if (order == null || items == null || items.isEmpty()) {
            throw new BizException("库存预占缺少订单或明细");
        }
        // 按 productId 排序预占，避免并发下单对同一商品集交叉加锁造成死锁。
        List<OrderItem> sorted = items.stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .collect(Collectors.toList());
        for (OrderItem item : sorted) {
            if (item.getId() == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BizException("库存预占明细数据非法，orderItemId=" + item.getId());
            }
            InventoryReservation reservation = new InventoryReservation();
            reservation.setReservationNo(OrderNoUtils.getReservationNo());
            reservation.setOrderNo(item.getOrderNo());
            reservation.setOrderItemId(item.getId());
            reservation.setProductId(item.getProductId());
            reservation.setQuantity(item.getQuantity());
            reservation.setStatus(ReservationStatus.LOCKED.getType());
            reservation.setExpireTime(order.getExpireTime());
            try {
                // 唯一键 uk_reservation_order_item(order_item_id) 兼作幂等闸门：已预占则整项跳过。
                reservationMapper.insert(reservation);
            } catch (DuplicateKeyException e) {
                log.info("库存预占已存在，幂等跳过，orderItemId={}", item.getId());
                continue;
            }
            int reserved = productMapper.reserveStock(item.getProductId(), item.getQuantity());
            if (reserved == 0) {
                // 抛出后整个下单事务回滚（含预占记录），可用库存不变。
                throw new BizException("库存不足，productId=" + item.getProductId());
            }
            insertTransaction(InventoryBizType.ORDER_RESERVE, item.getOrderNo(), item.getId(), null,
                    item.getProductId(), -item.getQuantity(), item.getQuantity(), 0);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitReservation(String orderNo) {
        int changed = reservationMapper.casTransition(orderNo,
                ReservationStatus.LOCKED.getType(), ReservationStatus.COMMITTED.getType(), "commitTime");
        List<InventoryReservation> reservations = listByOrderNo(orderNo);
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
    public void convertToSoldOnReceipt(String orderNo) {
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_no", orderNo).orderByAsc("id"));
        if (items.isEmpty()) {
            log.warn("确认收货结转未找到订单明细，跳过，orderNo={}", orderNo);
            return;
        }
        for (OrderItem item : items) {
            int qty = item.getQuantity() - (item.getRestockedQty() == null ? 0 : item.getRestockedQty());
            if (qty <= 0) {
                log.info("确认收货结转数量为0，跳过，orderNo={}, orderItemId={}", orderNo, item.getId());
                continue;
            }
            String bizNo = InventoryBizType.ORDER_SOLD.name() + ":" + orderNo + ":" + item.getId();
            Integer exists = transactionMapper.selectCount(new QueryWrapper<InventoryTransaction>().eq("biz_no", bizNo));
            if (exists != null && exists > 0) {
                log.info("确认收货结转流水已存在，幂等跳过，orderNo={}, orderItemId={}", orderNo, item.getId());
                continue;
            }
            int converted = productMapper.commitSoldStock(item.getProductId(), qty);
            if (converted == 0) {
                // 旧模型存量在途单的锁定已在支付时被扣减，无法结转：不阻塞收货，仅告警留痕。
                log.warn("确认收货结转失败（锁定库存不足），跳过，orderNo={}, orderItemId={}, productId={}, qty={}",
                        orderNo, item.getId(), item.getProductId(), qty);
                continue;
            }
            insertTransaction(InventoryBizType.ORDER_SOLD, orderNo, item.getId(), null,
                    item.getProductId(), 0, -qty, qty);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseReservation(String orderNo) {
        int changed = reservationMapper.casTransition(orderNo,
                ReservationStatus.LOCKED.getType(), ReservationStatus.RELEASED.getType(), "releaseTime");
        List<InventoryReservation> reservations = listByOrderNo(orderNo);
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
            int released = productMapper.releaseReservedStock(reservation.getProductId(), reservation.getQuantity());
            if (released == 0) {
                throw new BizException("库存预占释放失败，productId=" + reservation.getProductId());
            }
            insertTransaction(InventoryBizType.ORDER_RELEASE, orderNo, reservation.getOrderItemId(), null,
                    reservation.getProductId(), reservation.getQuantity(), -reservation.getQuantity(), 0);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restockForRefund(String refundNo, List<RefundItem> items, boolean afterReceipt) {
        if (refundNo == null || items == null || items.isEmpty()) {
            return;
        }
        for (RefundItem item : items) {
            String bizNo = InventoryBizType.REFUND_RESTOCK.name() + ":" + refundNo + ":" + item.getOrderItemId();
            Integer exists = transactionMapper.selectCount(new QueryWrapper<InventoryTransaction>().eq("biz_no", bizNo));
            if (exists != null && exists > 0) {
                log.info("退款回补流水已存在，幂等跳过，refundNo={}, orderItemId={}", refundNo, item.getOrderItemId());
                continue;
            }
            int added = orderItemMapper.addRestockedQty(item.getOrderItemId(), item.getRefundQty());
            if (added == 0) {
                throw new BizException("补库存数量超过可回补上限，orderItemId=" + item.getOrderItemId());
            }
            // 来源桶分流：未收货从锁定归还，已收货从已售归还。
            int moved = afterReceipt
                    ? productMapper.releaseSoldStock(item.getProductId(), item.getRefundQty())
                    : productMapper.releaseReservedStock(item.getProductId(), item.getRefundQty());
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
    public void writeOffLostForRefund(String refundNo, List<RefundItem> items, boolean afterReceipt) {
        if (refundNo == null || items == null || items.isEmpty()) {
            return;
        }
        for (RefundItem item : items) {
            String bizNo = InventoryBizType.REFUND_LOST.name() + ":" + refundNo + ":" + item.getOrderItemId();
            Integer exists = transactionMapper.selectCount(new QueryWrapper<InventoryTransaction>().eq("biz_no", bizNo));
            if (exists != null && exists > 0) {
                log.info("退款货损核销流水已存在，幂等跳过，refundNo={}, orderItemId={}", refundNo, item.getOrderItemId());
                continue;
            }
            // 累加 restocked_qty：防超核销守卫（restocked+qty ≤ refunded+frozen）；
            // 未收货场景同时使确认收货结转数量 quantity-restocked_qty 跳过已核销部分，避免部分退款时整笔结转 CAS 失败。
            int added = orderItemMapper.addRestockedQty(item.getOrderItemId(), item.getRefundQty());
            if (added == 0) {
                throw new BizException("货损核销数量超过可核销上限，orderItemId=" + item.getOrderItemId());
            }
            // 来源桶分流：未收货货在锁定桶（locked→lost），已收货货已售出（sold→lost，货留用户不回仓）。
            int written = afterReceipt
                    ? productMapper.writeOffSoldLostStock(item.getProductId(), item.getRefundQty())
                    : productMapper.writeOffLostStock(item.getProductId(), item.getRefundQty());
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
        transaction.setBizNo(bizType.name() + ":" + (refundNo != null ? refundNo : orderNo) + ":" + orderItemId);
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
            transactionMapper.insert(transaction);
        } catch (DuplicateKeyException e) {
            // 审计流水唯一键冲突：同一动作已落流水，幂等跳过。
            log.info("库存流水已存在，幂等跳过，bizNo={}", transaction.getBizNo());
        }
    }

    private List<InventoryReservation> listByOrderNo(String orderNo) {
        return reservationMapper.selectList(new QueryWrapper<InventoryReservation>().eq("order_no", orderNo).orderByAsc("id"));
    }
}
