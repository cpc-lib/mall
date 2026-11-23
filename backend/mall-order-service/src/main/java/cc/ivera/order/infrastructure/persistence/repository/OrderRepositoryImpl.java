package cc.ivera.order.infrastructure.persistence.repository;

import cc.ivera.order.domain.enums.FulfillmentStatus;
import cc.ivera.order.domain.enums.OrderLifecycleStatus;
import cc.ivera.order.domain.enums.OrderRefundStatus;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.order.infrastructure.persistence.converter.OrderInfoPOConverter;
import cc.ivera.order.infrastructure.persistence.converter.OrderItemPOConverter;
import cc.ivera.order.infrastructure.persistence.mapper.OrderInfoMapper;
import cc.ivera.order.infrastructure.persistence.mapper.OrderItemMapper;
import cc.ivera.order.infrastructure.persistence.po.OrderInfoPO;
import cc.ivera.order.infrastructure.persistence.po.OrderItemPO;
import cc.ivera.payment.domain.enums.PayStatus;
import cc.ivera.shared.domain.lock.FenceTokenHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单聚合仓储实现：MP Wrapper/XML CAS 全部收口于此，领域层只见端口。
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;

    public OrderRepositoryImpl(OrderInfoMapper orderInfoMapper, OrderItemMapper orderItemMapper) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public void save(OrderInfo order) {
        OrderInfoPO po = OrderInfoPOConverter.toPO(order);
        po.setFenceToken(FenceTokenHolder.get());
        orderInfoMapper.insert(po);
        // id/审计字段回填领域对象（下单响应 VO 直接序列化聚合根）。
        order.setId(po.getId());
        order.setCreateTime(po.getCreateTime());
        order.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void saveItem(OrderItem item) {
        OrderItemPO po = OrderItemPOConverter.toPO(item);
        orderItemMapper.insert(po);
        item.setId(po.getId());
        item.setCreateTime(po.getCreateTime());
        item.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        return OrderInfoPOConverter.toDomain(orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)));
    }

    @Override
    public OrderInfo findByOrderNoAndUserId(String orderNo, Long userId) {
        return OrderInfoPOConverter.toDomain(orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getUserId, userId)));
    }

    @Override
    public OrderInfo findLatestNoPayOrder(Long productId, String paymentType, String legacyStatus, Long paymentAppId) {
        return OrderInfoPOConverter.toDomain(
            orderInfoMapper.selectLatestNoPayOrder(productId, paymentType, legacyStatus, paymentAppId));
    }

    @Override
    public List<OrderInfo> listAllByCreateTimeDesc() {
        return orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfoPO>()
                .orderByDesc(OrderInfoPO::getCreateTime))
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderInfo> listByUserIdPage(Long userId, int limit, long offset) {
        return orderInfoMapper.selectUserOrderPage(userId, limit, offset)
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByUserId(Long userId) {
        Long count = orderInfoMapper.countUserOrders(userId);
        return count == null ? 0L : count;
    }

    @Override
    public List<OrderInfo> searchAdmin(String payStatus, String orderStatus, String fulfillmentStatus,
                                       String orderNoLike, Long userId, Date startTime, Date endTime,
                                       int limit, long offset) {
        return orderInfoMapper.selectAdminOrderList(payStatus, orderStatus, fulfillmentStatus,
                orderNoLike, userId, startTime, endTime, limit, offset)
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countAdminOrders(String payStatus, String orderStatus, String fulfillmentStatus,
                                 String orderNoLike, Long userId, Date startTime, Date endTime) {
        Long count = orderInfoMapper.countAdminOrderList(payStatus, orderStatus, fulfillmentStatus,
                orderNoLike, userId, startTime, endTime);
        return count == null ? 0L : count;
    }

    @Override
    public List<OrderInfo> listWaitShipPage(int limit, long offset) {
        return orderInfoMapper.selectList(waitShipQuery()
                .orderByAsc(OrderInfoPO::getPaidTime)
                .orderByAsc(OrderInfoPO::getId)
                .last("LIMIT " + limit + " OFFSET " + offset))
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countWaitShip() {
        Long count = orderInfoMapper.selectCount(waitShipQuery());
        return count == null ? 0L : count;
    }

    private LambdaQueryWrapper<OrderInfoPO> waitShipQuery() {
        return new LambdaQueryWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getPayStatus, PayStatus.PAID.getType())
            .eq(OrderInfoPO::getFulfillmentStatus, FulfillmentStatus.WAIT_SHIP.getType())
            .and(w -> w.isNull(OrderInfoPO::getRefundStatus)
                .or().eq(OrderInfoPO::getRefundStatus, OrderRefundStatus.NONE.getType()));
    }

    @Override
    public List<OrderInfo> listTimeoutNotPay(Date cutoff) {
        // 纳入 PAYING：用户打开收银台后超时未付也必须被关单兜底（MQ 延迟关单失败时）
        // 额外约束 order_status = WAIT_PAY 只关还在待支付生命周期的订单，避免误扫
        LambdaQueryWrapper<OrderInfoPO> q = new LambdaQueryWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getLegacyStatus, OrderStatus.NOTPAY.getType())
            .in(OrderInfoPO::getPayStatus, PayStatus.UNPAID.getType(), PayStatus.PAYING.getType())
            .eq(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.WAIT_PAY.getType())
            .lt(OrderInfoPO::getCreateTime, cutoff)
            .orderByAsc(OrderInfoPO::getCreateTime)
            .last("limit 100");
        List<OrderInfoPO> rows = orderInfoMapper.selectList(q);
        return rows.stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> listItemsByOrderNo(String orderNo) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemPO>()
                .eq(OrderItemPO::getOrderNo, orderNo)
                .orderByAsc(OrderItemPO::getId))
            .stream().map(OrderItemPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> listItemsByOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemPO>()
                .in(OrderItemPO::getOrderNo, orderNos)
                .orderByAsc(OrderItemPO::getId))
            .stream().map(OrderItemPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public OrderItem findItemById(Long itemId) {
        return OrderItemPOConverter.toDomain(orderItemMapper.selectById(itemId));
    }

    @Override
    public boolean updateCodeUrlIfAbsent(String orderNo, String codeUrl) {
        LambdaUpdateWrapper<OrderInfoPO> wrapper = new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .and(w -> w.isNull(OrderInfoPO::getCodeUrl).or().eq(OrderInfoPO::getCodeUrl, ""));
        applyFence(wrapper);
        OrderInfoPO po = new OrderInfoPO();
        po.setCodeUrl(codeUrl);
        return orderInfoMapper.update(po, wrapper) > 0;
    }

    @Override
    public boolean casUnpaidToPaying(String orderNo) {
        return orderInfoMapper.casUnpaidToPaying(orderNo, FenceTokenHolder.get()) > 0;
    }

    @Override
    public void updateLegacyStatus(String orderNo, String legacyStatus) {
        OrderInfoPO po = new OrderInfoPO();
        po.setLegacyStatus(legacyStatus);
        orderInfoMapper.update(po, new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo));
    }

    @Override
    public boolean casLegacyStatus(String orderNo, String currentLegacyStatus, OrderInfo patch) {
        LambdaUpdateWrapper<OrderInfoPO> wrapper = new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getLegacyStatus, currentLegacyStatus);
        applyFence(wrapper);
        // 支付成功 CAS 同步实付金额以订单应收为准（金额一致性已由各渠道 notify 校验保证）。
        if (patch.isApplyPaidAmountFromTotalFee()) {
            wrapper.setSql("paid_amount = total_fee");
        }
        return orderInfoMapper.update(OrderInfoPOConverter.toPO(patch), wrapper) > 0;
    }

    @Override
    public int casFulfillment(String orderNo, String from, String to) {
        LambdaUpdateWrapper<OrderInfoPO> wrapper = new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getFulfillmentStatus, from)
            .set(OrderInfoPO::getFulfillmentStatus, to);
        applyFence(wrapper);
        return orderInfoMapper.update(null, wrapper);
    }

    @Override
    public int cancelIfUnpaid(String orderNo) {
        LambdaUpdateWrapper<OrderInfoPO> wrapper = new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.WAIT_PAY.getType())
            .eq(OrderInfoPO::getPayStatus, PayStatus.UNPAID.getType())
            .set(OrderInfoPO::getLegacyStatus, OrderStatus.CANCEL.getType())
            .set(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.CLOSED.getType())
            .set(OrderInfoPO::getFulfillmentStatus, FulfillmentStatus.CANCELLED.getType())
            .set(OrderInfoPO::getUpdateTime, new Date());
        applyFence(wrapper);
        return orderInfoMapper.update(null, wrapper);
    }

    @Override
    public int freezeOrderRefund(String orderNo, Integer amount) {
        return orderInfoMapper.freezeOrderRefund(orderNo, amount, FenceTokenHolder.get());
    }

    @Override
    public int releaseOrderRefundFreeze(String orderNo, Integer amount) {
        return orderInfoMapper.releaseOrderRefundFreeze(orderNo, amount, FenceTokenHolder.get());
    }

    @Override
    public int settleOrderRefund(String orderNo, Integer amount) {
        return orderInfoMapper.settleOrderRefund(orderNo, amount, FenceTokenHolder.get());
    }

    @Override
    public int applyOrderRefundStatus(String orderNo) {
        return orderInfoMapper.applyOrderRefundStatus(orderNo, FenceTokenHolder.get());
    }

    @Override
    public int freezeItemRefund(Long itemId, Integer qty, Integer amount) {
        return orderItemMapper.freezeItemRefund(itemId, qty, amount);
    }

    @Override
    public int releaseItemRefundFreeze(Long itemId, Integer qty, Integer amount) {
        return orderItemMapper.releaseItemRefundFreeze(itemId, qty, amount);
    }

    @Override
    public int settleItemRefund(Long itemId, Integer qty, Integer amount) {
        return orderItemMapper.settleItemRefund(itemId, qty, amount);
    }

    @Override
    public int addRestockedQty(Long itemId, Integer qty) {
        return orderItemMapper.addRestockedQty(itemId, qty);
    }

    @Override
    public int casCloseIfFullRefunded(String orderNo) {
        LambdaUpdateWrapper<OrderInfoPO> wrapper = new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.ACTIVE.getType())
            .eq(OrderInfoPO::getRefundStatus, OrderRefundStatus.FULL_REFUNDED.getType())
            .set(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.CLOSED.getType())
            .set(OrderInfoPO::getUpdateTime, new Date());
        applyFence(wrapper);
        return orderInfoMapper.update(null, wrapper);
    }

    /**
     * 围栏令牌 CAS：持锁时追加 fence_token <= currentToken 条件并回写令牌，
     * 防止锁过期（异步复制延迟/进程停顿）后旧持有者覆盖新数据；同一把锁内允许连续推进。
     */
    private void applyFence(LambdaUpdateWrapper<OrderInfoPO> wrapper) {
        Long fenceToken = FenceTokenHolder.get();
        if (fenceToken != null) {
            wrapper.le(OrderInfoPO::getFenceToken, fenceToken);
            wrapper.set(OrderInfoPO::getFenceToken, fenceToken);
        }
    }
}
