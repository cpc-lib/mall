package cc.ivera.order.infrastructure.persistence.repository;

import cc.ivera.order.domain.enums.FulfillmentStatus;
import cc.ivera.order.domain.enums.OrderLifecycleStatus;
import cc.ivera.order.domain.enums.OrderRefundStatus;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.payment.domain.enums.PayStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.order.infrastructure.persistence.converter.OrderInfoPOConverter;
import cc.ivera.order.infrastructure.persistence.converter.OrderItemPOConverter;
import cc.ivera.order.infrastructure.persistence.mapper.OrderInfoMapper;
import cc.ivera.order.infrastructure.persistence.mapper.OrderItemMapper;
import cc.ivera.order.infrastructure.persistence.po.OrderInfoPO;
import cc.ivera.order.infrastructure.persistence.po.OrderItemPO;
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
    public OrderInfo findByOrderNoForUpdate(String orderNo) {
        return OrderInfoPOConverter.toDomain(orderInfoMapper.selectByOrderNoForUpdate(orderNo));
    }

    @Override
    public OrderInfo findByOrderNoAndUserId(String orderNo, Long userId) {
        return OrderInfoPOConverter.toDomain(orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getUserId, userId)));
    }

    @Override
    public OrderInfo findNoPayForUpdate(Long productId, String paymentType, String legacyStatus, Long paymentAppId) {
        return OrderInfoPOConverter.toDomain(
            orderInfoMapper.selectNoPayOrderForUpdate(productId, paymentType, legacyStatus, paymentAppId));
    }

    @Override
    public List<OrderInfo> listAllByCreateTimeDesc() {
        return orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfoPO>()
                .orderByDesc(OrderInfoPO::getCreateTime))
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderInfo> listByUserIdCreateTimeDesc(Long userId) {
        return orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfoPO>()
                .eq(OrderInfoPO::getUserId, userId)
                .orderByDesc(OrderInfoPO::getCreateTime))
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderInfo> searchAdmin(String payStatus, String orderStatus, String fulfillmentStatus,
                                       String orderNoLike, Long userId, Date startTime, Date endTime) {
        return orderInfoMapper.selectAdminOrderList(payStatus, orderStatus, fulfillmentStatus,
                orderNoLike, userId, startTime, endTime)
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderInfo> listWaitShip() {
        return orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfoPO>()
                .eq(OrderInfoPO::getPayStatus, PayStatus.PAID.getType())
                .eq(OrderInfoPO::getFulfillmentStatus, FulfillmentStatus.WAIT_SHIP.getType())
                .and(w -> w.isNull(OrderInfoPO::getRefundStatus)
                    .or().eq(OrderInfoPO::getRefundStatus, OrderRefundStatus.NONE.getType()))
                .orderByAsc(OrderInfoPO::getPaidTime))
            .stream().map(OrderInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderInfo> listTimeoutNotPay(Date cutoff) {
        LambdaQueryWrapper<OrderInfoPO> q = new LambdaQueryWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getLegacyStatus, OrderStatus.NOTPAY.getType())
            .lt(OrderInfoPO::getCreateTime, cutoff)
            .orderByAsc(OrderInfoPO::getCreateTime)
            .last("limit 100");
        List<OrderInfoPO> rows;
        try {
            rows = orderInfoMapper.selectList(q);
        } catch (RuntimeException dmLimitSyntax) {
            // DM8 对 LIMIT 语法兼容性依赖模式；退化为不带 LIMIT 的扫描，不改变业务时序。
            rows = orderInfoMapper.selectList(new LambdaQueryWrapper<OrderInfoPO>()
                .eq(OrderInfoPO::getLegacyStatus, OrderStatus.NOTPAY.getType())
                .lt(OrderInfoPO::getCreateTime, cutoff)
                .orderByAsc(OrderInfoPO::getCreateTime));
        }
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
    public OrderItem findItemById(Long itemId) {
        return OrderItemPOConverter.toDomain(orderItemMapper.selectById(itemId));
    }

    @Override
    public boolean updateCodeUrlIfAbsent(String orderNo, String codeUrl) {
        LambdaUpdateWrapper<OrderInfoPO> wrapper = new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .and(w -> w.isNull(OrderInfoPO::getCodeUrl).or().eq(OrderInfoPO::getCodeUrl, ""));
        OrderInfoPO po = new OrderInfoPO();
        po.setCodeUrl(codeUrl);
        return orderInfoMapper.update(po, wrapper) > 0;
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
        // 支付成功 CAS 同步实付金额以订单应收为准（金额一致性已由各渠道 notify 校验保证）。
        if (patch.isApplyPaidAmountFromTotalFee()) {
            wrapper.setSql("paid_amount = total_fee");
        }
        return orderInfoMapper.update(OrderInfoPOConverter.toPO(patch), wrapper) > 0;
    }

    @Override
    public int casFulfillment(String orderNo, String from, String to) {
        return orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getFulfillmentStatus, from)
            .set(OrderInfoPO::getFulfillmentStatus, to));
    }

    @Override
    public int freezeOrderRefund(String orderNo, Integer amount) {
        return orderInfoMapper.freezeOrderRefund(orderNo, amount);
    }

    @Override
    public int releaseOrderRefundFreeze(String orderNo, Integer amount) {
        return orderInfoMapper.releaseOrderRefundFreeze(orderNo, amount);
    }

    @Override
    public int settleOrderRefund(String orderNo, Integer amount) {
        return orderInfoMapper.settleOrderRefund(orderNo, amount);
    }

    @Override
    public int applyOrderRefundStatus(String orderNo) {
        return orderInfoMapper.applyOrderRefundStatus(orderNo);
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
        return orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfoPO>()
            .eq(OrderInfoPO::getOrderNo, orderNo)
            .eq(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.ACTIVE.getType())
            .eq(OrderInfoPO::getRefundStatus, OrderRefundStatus.FULL_REFUNDED.getType())
            .set(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.CLOSED.getType())
            .set(OrderInfoPO::getUpdateTime, new Date()));
    }
}
