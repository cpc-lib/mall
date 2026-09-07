package cc.ivera.service.impl;

import cc.ivera.entity.LocalMessage;
import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderItem;
import cc.ivera.entity.Product;
import cc.ivera.enums.FulfillmentStatus;
import cc.ivera.enums.OrderLifecycleStatus;
import cc.ivera.enums.OrderRefundStatus;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayStatus;
import cc.ivera.exception.BizException;
import cc.ivera.lock.DistributedLockTemplate;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.mapper.OrderItemMapper;
import cc.ivera.mapper.PaymentOrderMapper;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.service.InventoryService;
import cc.ivera.service.LocalMessageService;
import cc.ivera.service.OrderCloseMessageService;
import cc.ivera.service.OrderInfoService;
import cc.ivera.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    private static final long ORDER_CREATE_LOCK_WAIT_MS = 3000L;
    private static final long ORDER_CREATE_LOCK_LEASE_MS = 10000L;

    /** 本地订单未支付超时（分钟），与 MQ 延迟关单 TTL、定时兜底扫描共用同一配置。 */
    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    private final ProductMapper productMapper;

    private final OrderItemMapper orderItemMapper;

    private final PaymentOrderMapper paymentOrderMapper;

    private final InventoryService inventoryService;

    private final LocalMessageService localMessageService;

    private final OrderCloseMessageService orderCloseMessageService;

    private final DistributedLockTemplate distributedLockTemplate;

    private final TransactionTemplate transactionTemplate;

    public OrderInfoServiceImpl(
        ProductMapper productMapper,
        OrderItemMapper orderItemMapper,
        PaymentOrderMapper paymentOrderMapper,
        OrderCloseMessageService orderCloseMessageService,
        InventoryService inventoryService,
        LocalMessageService localMessageService,
        DistributedLockTemplate distributedLockTemplate,
        TransactionTemplate transactionTemplate
    ) {
        this.productMapper = productMapper;
        this.orderItemMapper = orderItemMapper;
        this.paymentOrderMapper = paymentOrderMapper;
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
                ORDER_CREATE_LOCK_LEASE_MS,
                () -> transactionTemplate.execute(status -> doCreateOrReuseOrder(productId, paymentType, paymentAppId, paymentChannelCode))
        );
    }

    private OrderInfo doCreateOrReuseOrder(Long productId, String paymentType, Long paymentAppId, String paymentChannelCode) {
        // 第一层防护：Redis 分布式锁，挡住多实例并发。
        // 第二层防护：select ... for update，挡住同库事务并发。
        OrderInfo noPayOrder = baseMapper.selectNoPayOrderForUpdate(
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

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException("商品不存在");
        }

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setTitle(product.getTitle());
        orderInfo.setOrderNo(OrderNoUtils.getOrderNo());
        orderInfo.setProductId(productId);
        orderInfo.setTotalFee(product.getPrice());
        orderInfo.setLegacyStatus(OrderStatus.NOTPAY.getType());
        orderInfo.setPaymentType(paymentType);
        orderInfo.setPaymentAppId(paymentAppId);
        orderInfo.setPaymentChannelCode(paymentChannelCode);
        orderInfo.setVersion(0);
        orderInfo.setExpireTime(new java.util.Date(System.currentTimeMillis() + orderExpireMinutes * 60 * 1000));
        // V2 四维状态显式落库；快速购买无收货人输入，用模拟值（物流模拟）。
        orderInfo.setOrderStatus(OrderLifecycleStatus.WAIT_PAY.getType());
        orderInfo.setPayStatus(PayStatus.UNPAID.getType());
        orderInfo.setFulfillmentStatus(FulfillmentStatus.WAIT_SHIP.getType());
        orderInfo.setRefundStatus(OrderRefundStatus.NONE.getType());
        orderInfo.setReceiverName("模拟收货人");
        orderInfo.setReceiverPhone("13800000000");
        orderInfo.setReceiverAddress("模拟地址-请勿发货");

        try {
            baseMapper.insert(orderInfo);
            // 旧的单商品入口也同步写订单快照明细，保证后续退款永远基于下单价而不是当前商品价。
            OrderItem item = new OrderItem();
            item.setOrderId(orderInfo.getId());
            item.setOrderNo(orderInfo.getOrderNo());
            item.setProductId(product.getId());
            item.setProductTitle(product.getTitle());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(1);
            item.setRefundedQty(0);
            // V2 成交快照金额（退款资金上限依据）：无优惠券，pay_amount=unit_price*quantity。
            item.setDealUnitAmount(product.getPrice());
            item.setOriginalTotalAmount(product.getPrice());
            item.setDiscountAmount(0);
            item.setPayAmount(product.getPrice());
            item.setRefundFrozenQty(0); item.setRefundFrozenAmount(0); item.setRefundedAmount(0); item.setRestockedQty(0);
            orderItemMapper.insert(item);
            // V2：下单同步预占库存（同事务），库存不足整体回滚，替代旧 MQ 异步扣减。
            inventoryService.reserveForOrder(orderInfo, java.util.Collections.singletonList(item));
        } catch (DuplicateKeyException e) {
            // 极小概率订单号碰撞，或者历史数据存在并发写入时，兜底重新查询未支付订单。
            log.warn("订单插入触发唯一约束，尝试复用已有订单，productId={}, paymentType={}", productId, paymentType, e);
            OrderInfo existOrder = baseMapper.selectNoPayOrderForUpdate(
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

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("order_no", orderNo)
                .and(wrapper -> wrapper.isNull("code_url").or().eq("code_url", ""));

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCodeUrl(codeUrl);

        int updated = baseMapper.update(orderInfo, updateWrapper);
        if (updated == 0) {
            log.info("订单二维码已存在，本次不覆盖，orderNo={}", orderNo);
        }
    }

    @Override
    public List<OrderInfo> listOrderByCreateTimeDesc() {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<OrderInfo>().orderByDesc("create_time");
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus) {
        if (!StringUtils.hasText(orderNo) || orderStatus == null) {
            return;
        }
        log.info("更新订单状态 ===> orderNo={}, status={}", orderNo, orderStatus.getType());

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("order_no", orderNo);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setLegacyStatus(orderStatus.getType());

        baseMapper.update(orderInfo, updateWrapper);
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

        UpdateWrapper<OrderInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("order_no", orderNo);
        updateWrapper.eq("legacy_status", currentStatus.getType());

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setLegacyStatus(targetStatus.getType());
        // V2 四维状态同步落库，legacy_status 仅供审计。
        if (targetStatus == OrderStatus.SUCCESS) {
            orderInfo.setOrderStatus(OrderLifecycleStatus.ACTIVE.getType());
            orderInfo.setPayStatus(PayStatus.PAID.getType());
            orderInfo.setPaidTime(new java.util.Date());
            // 实付金额以订单应收为准（金额一致性已由各渠道 notify 校验保证）。
            updateWrapper.setSql("paid_amount = total_fee");
        } else if (targetStatus == OrderStatus.CLOSED || targetStatus == OrderStatus.CANCEL) {
            orderInfo.setOrderStatus(OrderLifecycleStatus.CLOSED.getType());
        }

        boolean updated = baseMapper.update(orderInfo, updateWrapper) > 0;
        if (updated && currentStatus == OrderStatus.NOTPAY) {
            if (targetStatus == OrderStatus.SUCCESS) {
                commitReservationAfterCommit(orderNo);
            } else if (targetStatus == OrderStatus.CLOSED || targetStatus == OrderStatus.CANCEL) {
                // 同事务关闭该订单全部活跃支付单，防止关闭后渠道侧仍可支付。
                paymentOrderMapper.closeActiveByOrderNo(orderNo);
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
    public String getOrderStatus(String orderNo) {
        OrderInfo orderInfo = getOrderByOrderNo(orderNo);
        return orderInfo == null ? null : orderInfo.getLegacyStatus();
    }

    @Override
    public OrderInfo getOrderByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no", orderNo);
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public OrderInfo getOrderByOrderNoForUpdate(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return baseMapper.selectByOrderNoForUpdate(orderNo);
    }

    private void commitReservationAfterCommit(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            inventoryService.commitReservation(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override public void afterCommit() { inventoryService.commitReservation(orderNo); }
        });
    }

    private void releaseReservationAfterCommit(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            inventoryService.releaseReservation(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override public void afterCommit() { inventoryService.releaseReservation(orderNo); }
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
