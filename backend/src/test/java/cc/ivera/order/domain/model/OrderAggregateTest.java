package cc.ivera.order.domain.model;

import cc.ivera.order.domain.enums.*;
import cc.ivera.payment.domain.enums.PayStatus;
import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * order 上下文聚合根/实体纯单元测试：不连数据库/Redis/MQ。
 * 锁定从 Service 等价搬入的状态规则：四维状态初始化、V1 视图映射、发货/收货守卫、legacy CAS 补丁。
 */
class OrderAggregateTest {

    private OrderInfo newOrder() {
        return OrderInfo.createNew("测试订单", "ORD1", 7L, 3L, 9900, "WXPAY", 1L, "WX_NATIVE",
            new Date(1_000_000L), "张三", "13800000000", "北京市海淀区");
    }

    @Test
    void createNewInitializesFourDimensionsAndLegacy() {
        OrderInfo o = newOrder();
        assertEquals(OrderStatus.NOTPAY.getType(), o.getLegacyStatus());
        assertEquals(OrderLifecycleStatus.WAIT_PAY.getType(), o.getOrderStatus());
        assertEquals(PayStatus.UNPAID.getType(), o.getPayStatus());
        assertEquals(FulfillmentStatus.WAIT_SHIP.getType(), o.getFulfillmentStatus());
        assertEquals(OrderRefundStatus.NONE.getType(), o.getRefundStatus());
        assertEquals(Integer.valueOf(0), o.getVersion());
        assertEquals("ORD1", o.getOrderNo());
        assertEquals(9900, o.getTotalFee());
        assertEquals("张三", o.getReceiverName());
    }

    @Test
    void isPayableOnlyWhenLegacyNotPay() {
        OrderInfo o = newOrder();
        assertTrue(o.isPayable());
        o.setLegacyStatus(OrderStatus.SUCCESS.getType());
        assertFalse(o.isPayable());
        o.setLegacyStatus(OrderStatus.CLOSED.getType());
        assertFalse(o.isPayable());
    }

    @Test
    void legacyViewStatusPrefersRefundStates() {
        OrderInfo o = newOrder();
        o.setRefundStatus(OrderRefundStatus.REFUNDING.getType());
        assertEquals(OrderStatus.REFUND_PROCESSING.getType(), o.toLegacyViewStatus());
        o.setRefundStatus(OrderRefundStatus.PARTIAL_REFUNDED.getType());
        assertEquals(OrderStatus.PARTIAL_REFUND.getType(), o.toLegacyViewStatus());
        o.setRefundStatus(OrderRefundStatus.FULL_REFUNDED.getType());
        assertEquals(OrderStatus.REFUND_SUCCESS.getType(), o.toLegacyViewStatus());
    }

    @Test
    void legacyViewStatusMapsLifecycleWhenNoRefund() {
        OrderInfo o = newOrder();
        assertEquals(OrderStatus.NOTPAY.getType(), o.toLegacyViewStatus());
        o.setOrderStatus(OrderLifecycleStatus.CLOSED.getType());
        assertEquals(OrderStatus.CLOSED.getType(), o.toLegacyViewStatus());
        o.setOrderStatus(OrderLifecycleStatus.ACTIVE.getType());
        assertEquals(OrderStatus.SUCCESS.getType(), o.toLegacyViewStatus());
        o.setOrderStatus(null);
        assertEquals(OrderStatus.NOTPAY.getType(), o.toLegacyViewStatus());
    }

    @Test
    void requireShippablePassesForPaidWaitShipNoShipment() {
        OrderInfo o = newOrder();
        o.setPayStatus(PayStatus.PAID.getType());
        o.requireShippable(false);
    }

    @Test
    void requireShippableRejectsEachGuardBranch() {
        // 未支付
        OrderInfo unpaid = newOrder();
        assertThrows(BizException.class, () -> unpaid.requireShippable(false));
        // 退款中
        OrderInfo refunding = newOrder();
        refunding.setPayStatus(PayStatus.PAID.getType());
        refunding.setRefundStatus(OrderRefundStatus.REFUNDING.getType());
        assertThrows(BizException.class, () -> refunding.requireShippable(false));
        // 已收货
        OrderInfo received = newOrder();
        received.setPayStatus(PayStatus.PAID.getType());
        received.setFulfillmentStatus(FulfillmentStatus.RECEIVED.getType());
        assertThrows(BizException.class, () -> received.requireShippable(false));
        // 已发货（非待发货的通用分支）
        OrderInfo shipped = newOrder();
        shipped.setPayStatus(PayStatus.PAID.getType());
        shipped.setFulfillmentStatus(FulfillmentStatus.SHIPPED.getType());
        assertThrows(BizException.class, () -> shipped.requireShippable(false));
        // 已存在物流单
        OrderInfo dup = newOrder();
        dup.setPayStatus(PayStatus.PAID.getType());
        assertThrows(BizException.class, () -> dup.requireShippable(true));
    }

    @Test
    void requireOpenForReceiptBlocksWhenRefundExists() {
        OrderInfo o = newOrder();
        o.requireOpenForReceipt();
        o.setRefundStatus(OrderRefundStatus.PARTIAL_REFUNDED.getType());
        assertThrows(BizException.class, o::requireOpenForReceipt);
    }

    @Test
    void legacyStatusPatchSuccessActivatesAndMarksPaidAmountFlag() {
        Date now = new Date();
        OrderInfo patch = OrderInfo.legacyStatusPatch(OrderStatus.SUCCESS, now);
        assertEquals(OrderStatus.SUCCESS.getType(), patch.getLegacyStatus());
        assertEquals(OrderLifecycleStatus.ACTIVE.getType(), patch.getOrderStatus());
        assertEquals(PayStatus.PAID.getType(), patch.getPayStatus());
        assertEquals(now, patch.getPaidTime());
        assertTrue(patch.isApplyPaidAmountFromTotalFee());
    }

    @Test
    void legacyStatusPatchCloseAndCancelCloseLifecycle() {
        OrderInfo closed = OrderInfo.legacyStatusPatch(OrderStatus.CLOSED, new Date());
        assertEquals(OrderLifecycleStatus.CLOSED.getType(), closed.getOrderStatus());
        assertNull(closed.getPayStatus());
        assertFalse(closed.isApplyPaidAmountFromTotalFee());
        OrderInfo cancelled = OrderInfo.legacyStatusPatch(OrderStatus.CANCEL, new Date());
        assertEquals(OrderLifecycleStatus.CLOSED.getType(), cancelled.getOrderStatus());
        assertFalse(cancelled.isApplyPaidAmountFromTotalFee());
    }

    @Test
    void itemSnapshotComputesAmountsAndZeroesRefundCounters() {
        OrderItem item = OrderItem.createSnapshot(11L, "ORD1", 3L, "商品A", 1500, 3);
        assertEquals(11L, item.getOrderId());
        assertEquals("ORD1", item.getOrderNo());
        assertEquals(3L, item.getProductId());
        assertEquals("商品A", item.getProductTitle());
        assertEquals(1500, item.getUnitPrice());
        assertEquals(3, item.getQuantity());
        assertEquals(1500, item.getDealUnitAmount());
        assertEquals(4500, item.getOriginalTotalAmount());
        assertEquals(0, item.getDiscountAmount());
        assertEquals(4500, item.getPayAmount());
        assertEquals(0, item.getRefundedQty());
        assertEquals(0, item.getRefundFrozenQty());
        assertEquals(0, item.getRefundFrozenAmount());
        assertEquals(0, item.getRefundedAmount());
        assertEquals(0, item.getRestockedQty());
    }

    @Test
    void shipmentCreateShippedUsesMockCarrierAndShippedStatus() {
        Date now = new Date();
        OrderShipment s = OrderShipment.createShipped("SH1", "ORD1", "MOCK123", now);
        assertEquals("SH1", s.getShipmentNo());
        assertEquals("ORD1", s.getOrderNo());
        assertEquals("模拟快递", s.getLogisticsCompany());
        assertEquals("MOCK123", s.getTrackingNo());
        assertEquals(ShipmentStatus.SHIPPED.getType(), s.getStatus());
        assertEquals(now, s.getShippedTime());
        assertEquals("模拟物流商发货", s.getRemark());
        assertFalse(s.isReceived());
        s.setStatus(ShipmentStatus.RECEIVED.getType());
        assertTrue(s.isReceived());
    }

    @Test
    void shipmentRequireDeliveredForReceiptOnlyAllowsDelivered() {
        Date now = new Date();
        OrderShipment shipped = OrderShipment.createShipped("SH1", "ORD1", "MOCK123", now);
        assertThrows(BizException.class, () -> shipped.requireDeliveredForReceipt("ORD1"));
        shipped.setStatus(ShipmentStatus.DELIVERED.getType());
        shipped.requireDeliveredForReceipt("ORD1");
    }
}
