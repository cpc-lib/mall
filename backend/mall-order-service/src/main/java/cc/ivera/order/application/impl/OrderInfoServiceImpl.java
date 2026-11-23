package cc.ivera.order.application.impl;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.application.OrderCloseMessageService;
import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.application.OrderPaymentResult;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.api.payment.PaymentOrderCommandApi;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ReserveLine;
import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import cc.ivera.shared.web.PageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class OrderInfoServiceImpl implements OrderInfoService, OrderPaymentApi {

    private static final long ORDER_CREATE_LOCK_WAIT_MS = 3000L;
    private final ProductQueryApi productRepository;
    private final OrderRepository orderRepository;
    private final PaymentOrderCommandApi paymentOrderRepository;
    private final InventoryService inventoryService;
    private final LocalMessageService localMessageService;
    private final OrderCloseMessageService orderCloseMessageService;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    /**
     * 本地订单未支付超时（分钟），与 MQ 延迟关单 TTL、定时兜底扫描共用同一配置。
     */
    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    public OrderInfoServiceImpl(
        ProductQueryApi productRepository,
        OrderRepository orderRepository,
        PaymentOrderCommandApi paymentOrderRepository,
        OrderCloseMessageService orderCloseMessageService,
        InventoryService inventoryService,
        LocalMessageService localMessageService,
        DistributedLockTemplate distributedLockTemplate,
        TransactionTemplate transactionTemplate
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.orderCloseMessageService = orderCloseMessageService;
        this.inventoryService = inventoryService;
        this.localMessageService = localMessageService;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public OrderInfo createOrReuseOrder(Long productId, String paymentType) {
        return createOrReuseOrder(productId, paymentType, null, null);
    }

    @Override
    public OrderInfo createOrReuseOrder(Long productId, String paymentType, Long paymentAppId, String paymentChannelCode) {
        validateCreateOrderParams(productId, paymentType);

        String lockKey = buildCreateOrderLockKey(productId, paymentType, paymentAppId);
        return distributedLockTemplate.execute(
            lockKey,
            ORDER_CREATE_LOCK_WAIT_MS,
            () -> transactionTemplate.execute(status -> doCreateOrReuseOrder(productId, paymentType, paymentAppId, paymentChannelCode))
        );
    }

    private OrderInfo doCreateOrReuseOrder(Long productId, String paymentType, Long paymentAppId, String paymentChannelCode) {
        // 并发防护：同维度（商品+支付方式+支付应用）Redis 分布式锁硬串行（拿锁失败即拒绝），
        // 本查询只在锁内读取可复用订单；RC 隔离级下行锁对“无行可锁/间隙插入”无防护，故不加 for update。
        OrderInfo noPayOrder = orderRepository.findLatestNoPayOrder(
            productId,
            paymentType,
            OrderStatus.NOTPAY.getType(),
            paymentAppId
        );
        if (noPayOrder != null) {
            log.info("复用未支付订单，productId={}, paymentType={}, orderNo={}",
                productId, paymentType, noPayOrder.getOrderNo());
            return noPayOrder;
        }

        Product product = productRepository.findById(productId);
        if (product == null) {
            throw new BizException("商品不存在");
        }

        Date expireTime = new Date(System.currentTimeMillis() + orderExpireMinutes * 60 * 1000);
        OrderInfo orderInfo = OrderInfo.createNew(
            product.getTitle(),
            OrderNoUtils.getOrderNo(),
            null,
            productId,
            product.getPrice(),
            paymentType,
            paymentAppId,
            paymentChannelCode,
            expireTime,
            // V2 快速购买无收货人输入，用模拟值（物流模拟）。
            "模拟收货人",
            "13800000000",
            "模拟地址-请勿发货");

        try {
            orderRepository.save(orderInfo);
            // 旧的单商品入口也同步写订单快照明细，保证后续退款永远基于下单价而不是当前商品价。
            OrderItem item = OrderItem.createSnapshot(orderInfo.getId(), orderInfo.getOrderNo(),
                product.getId(), product.getTitle(), product.getPrice(), 1);
            orderRepository.saveItem(item);
            // V2：下单同步预占库存（远程库存服务），库存不足整体回滚；
            // 预占成功后本地事务若回滚，afterCompletion 补偿释放。
            inventoryService.reserveForOrder(orderInfo.getOrderNo(), orderInfo.getExpireTime(),
                Collections.singletonList(new ReserveLine(item.getId(), productId, 1)));
            afterRollback(() -> inventoryService.releaseReservation(orderInfo.getOrderNo()));
        } catch (DuplicateKeyException e) {
            // 极小概率订单号碰撞，或者历史数据存在并发写入时，兜底重新查询未支付订单。
            log.warn("订单插入触发唯一约束，尝试复用已有订单，productId={}, paymentType={}", productId, paymentType, e);
            OrderInfo existOrder = orderRepository.findLatestNoPayOrder(
                productId,
                paymentType,
                OrderStatus.NOTPAY.getType(),
                paymentAppId
            );
            if (existOrder != null) {
                return existOrder;
            }
            throw e;
        }

        // 事务性发件箱：延迟关单消息落库本地消息表（本方法无包裹事务，落库后立即投递；失败由发件箱定时重试兜底）
        orderCloseMessageService.sendCloseOrderMessage(orderInfo.getOrderNo(), orderInfo.getPaymentType());
        return orderInfo;
    }

    @Override
    public void saveCodeUrl(String orderNo, String codeUrl) {
        if (!StringUtils.hasText(orderNo) || !StringUtils.hasText(codeUrl)) {
            return;
        }
        boolean updated = orderRepository.updateCodeUrlIfAbsent(orderNo, codeUrl);
        if (!updated) {
            log.info("订单二维码已存在，本次不覆盖，orderNo={}", orderNo);
        }
    }

    @Override
    public List<OrderInfo> listOrderByCreateTimeDesc() {
        return orderRepository.listAllByCreateTimeDesc();
    }

    @Override
    public void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus) {
        if (!StringUtils.hasText(orderNo) || orderStatus == null) {
            return;
        }
        log.info("更新订单状态 ===> orderNo={}, status={}", orderNo, orderStatus.getType());
        orderRepository.updateLegacyStatus(orderNo, orderStatus.getType());
    }

    @Override
    public boolean updateStatusByOrderNoIfStatus(String orderNo, OrderStatus currentStatus, OrderStatus targetStatus) {
        if (!StringUtils.hasText(orderNo) || currentStatus == null || targetStatus == null) {
            return false;
        }

        log.info("条件更新订单状态 ===> orderNo={}, {} -> {}",
            orderNo,
            currentStatus.getType(),
            targetStatus.getType());

        // V2 四维状态补丁随 legacy CAS 同步落库，legacy_status 仅供审计。
        OrderInfo patch = OrderInfo.legacyStatusPatch(targetStatus, new Date());
        boolean updated = orderRepository.casLegacyStatus(orderNo, currentStatus.getType(), patch);
        if (updated && currentStatus == OrderStatus.NOTPAY) {
            if (targetStatus == OrderStatus.SUCCESS) {
                commitReservationAfterCommit(orderNo);
            } else if (targetStatus == OrderStatus.CLOSED || targetStatus == OrderStatus.CANCEL) {
                // 同事务关闭该订单全部活跃支付单，防止关闭后渠道侧仍可支付。
                paymentOrderRepository.closeActiveByOrderNo(orderNo);
                releaseReservationAfterCommit(orderNo);
                // 关单收口：无论由 MQ 延迟关单消费者还是定时兜底对账（TimeoutOrderCloseScheduler）触发，
                // 订单一旦离开 NOTPAY，其延迟关单发件箱消息即已完成使命，同事务回写 CONSUMED，
                // 与订单状态原子提交，避免定时关单已释放库存而 t_local_message 仍悬挂 PENDING。
                localMessageService.markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, orderNo);
            }
        }
        return updated;
    }

    @Override
    public OrderPaymentResult markPaid(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return OrderPaymentResult.NOT_FOUND;
        }
        OrderInfo order = getOrderByOrderNo(orderNo);
        if (order == null) {
            return OrderPaymentResult.NOT_FOUND;
        }
        if (OrderStatus.SUCCESS.getType().equals(order.getLegacyStatus())) {
            return OrderPaymentResult.ALREADY_PAID;
        }
        if (!OrderStatus.NOTPAY.getType().equals(order.getLegacyStatus())) {
            return OrderPaymentResult.CLOSED;
        }
        if (updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.SUCCESS)) {
            return OrderPaymentResult.PAID;
        }
        // CAS 失败说明并发方已推进状态：按最新状态返回。
        OrderInfo latest = getOrderByOrderNo(orderNo);
        if (latest != null && OrderStatus.SUCCESS.getType().equals(latest.getLegacyStatus())) {
            return OrderPaymentResult.ALREADY_PAID;
        }
        return OrderPaymentResult.CLOSED;
    }

    @Override
    public String getOrderStatus(String orderNo) {
        OrderInfo orderInfo = getOrderByOrderNo(orderNo);
        return orderInfo == null ? null : orderInfo.getLegacyStatus();
    }

    @Override
    public OrderInfo getOrderByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return orderRepository.findByOrderNo(orderNo);
    }

    @Override
    public PageVO<OrderInfo> listWaitShipOrders(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageVO<OrderInfo> result = new PageVO<>();
        result.setTotal(orderRepository.countWaitShip());
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(orderRepository.listWaitShipPage(safeSize, (long) (safePage - 1) * safeSize));
        return result;
    }

    @Override
    public List<OrderInfo> listTimeoutNotPayOrders(Date cutoff) {
        return orderRepository.listTimeoutNotPay(cutoff);
    }

    private void commitReservationAfterCommit(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            inventoryService.commitReservation(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                inventoryService.commitReservation(orderNo);
            }
        });
    }

    private void releaseReservationAfterCommit(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            inventoryService.releaseReservation(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                inventoryService.releaseReservation(orderNo);
            }
        });
    }

    /**
     * 本地事务未提交（回滚或异常）时执行补偿释放预占；无事务上下文时无事可回滚。
     */
    private void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) action.run();
            }
        });
    }

    private void validateCreateOrderParams(Long productId, String paymentType) {
        if (productId == null) {
            throw new BizException("商品ID不能为空");
        }
        if (!StringUtils.hasText(paymentType)) {
            throw new BizException("支付方式不能为空");
        }
    }

    private String buildCreateOrderLockKey(Long productId, String paymentType, Long paymentAppId) {
        return "payment:order:create:" + productId + ":" + paymentType + ":" + (paymentAppId == null ? "default" : paymentAppId);
    }
}
