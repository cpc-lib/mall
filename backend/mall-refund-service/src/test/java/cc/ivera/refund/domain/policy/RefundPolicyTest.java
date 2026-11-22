package cc.ivera.refund.domain.policy;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.refund.domain.enums.RefundType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退款纯域策略回归：尾差规则、防超退不变量与库存回补时点。
 * <p>不依赖 Spring / 数据库，验证 {@link RefundPolicy} 的领域规则本身。</p>
 */
class RefundPolicyTest {

    private static OrderItem item(int unitPrice, int quantity) {
        OrderItem item = OrderItem.createSnapshot(1L, "ORD-TEST", 1L, "测试商品", unitPrice, quantity);
        item.setId(1L);
        return item;
    }

    @Test
    @DisplayName("剩余可退数量扣除已退与冻结，且下限为 0")
    void refundableQty() {
        OrderItem item = item(1000, 3);
        item.setRefundedQty(1);
        item.setRefundFrozenQty(1);
        assertEquals(1, RefundPolicy.refundableQty(item));

        item.setRefundedQty(3);
        assertEquals(0, RefundPolicy.refundableQty(item));

        item.setRefundedQty(5);
        assertEquals(0, RefundPolicy.refundableQty(item), "脏数据不得返回负可退数量");
    }

    @Test
    @DisplayName("剩余可退金额扣除已退与冻结，且下限为 0")
    void refundableAmount() {
        OrderItem item = item(1000, 3);
        item.setRefundedAmount(1000);
        item.setRefundFrozenAmount(500);
        assertEquals(1500, RefundPolicy.refundableAmount(item));

        item.setRefundedAmount(3000);
        assertEquals(0, RefundPolicy.refundableAmount(item));
    }

    @Test
    @DisplayName("订单层剩余可退金额扣除已退与冻结，且下限为 0")
    void orderRefundableAmount() {
        OrderInfo order = new OrderInfo();
        order.setPaidAmount(5000);
        order.setRefundedAmount(1000);
        order.setRefundFrozenAmount(2000);
        assertEquals(2000, RefundPolicy.orderRefundableAmount(order));

        order.setRefundedAmount(5000);
        assertEquals(0, RefundPolicy.orderRefundableAmount(order));
    }

    @Test
    @DisplayName("非最后一件按成交均价向下取整，最后一件吃尾差拿剩余全额")
    void calcRefundAmountWithTailDifference() {
        OrderItem item = item(1000, 3);
        item.setPayAmount(1000); // 模拟优惠后行实付 1000，单价除不尽

        assertEquals(333, RefundPolicy.calcRefundAmount(item, 1), "非最后一件按 floor(实付/数量) 计算");

        item.setRefundedQty(1);
        item.setRefundedAmount(333);
        assertEquals(2, RefundPolicy.refundableQty(item));
        assertEquals(667, RefundPolicy.calcRefundAmount(item, 2), "最后一件取剩余全部可退金额");
    }

    @Test
    @DisplayName("正好退完全部数量时累计退款等于行实付")
    void fullRefundEqualsPayAmount() {
        OrderItem item = item(1999, 3);
        int first = RefundPolicy.calcRefundAmount(item, 1);
        item.setRefundedQty(1);
        item.setRefundedAmount(first);
        int rest = RefundPolicy.calcRefundAmount(item, 2);
        assertEquals(item.getPayAmount(), first + rest);
    }

    @Test
    @DisplayName("退款数量非法或超过可退数量时拒绝计算")
    void calcRefundAmountRejectsIllegalQty() {
        OrderItem item = item(1000, 3);
        assertThrows(IllegalArgumentException.class, () -> RefundPolicy.calcRefundAmount(item, 0));
        assertThrows(IllegalArgumentException.class, () -> RefundPolicy.calcRefundAmount(item, 4));

        OrderItem zeroQuantity = item(1000, 3);
        zeroQuantity.setQuantity(0);
        assertThrows(IllegalArgumentException.class, () -> RefundPolicy.calcRefundAmount(zeroQuantity, 1));
    }

    @Test
    @DisplayName("防超退不变量：已退+冻结不得超过数量或实付")
    void requireRefundInvariant() {
        OrderItem item = item(1000, 3);
        item.setRefundedQty(1);
        item.setRefundFrozenQty(1);
        item.setRefundedAmount(1000);
        item.setRefundFrozenAmount(1000);
        assertDoesNotThrow(() -> RefundPolicy.requireRefundInvariant(item));

        item.setRefundFrozenQty(3);
        assertThrows(IllegalStateException.class, () -> RefundPolicy.requireRefundInvariant(item));

        item.setRefundFrozenQty(0);
        item.setRefundFrozenAmount(2500);
        assertThrows(IllegalStateException.class, () -> RefundPolicy.requireRefundInvariant(item));
    }

    @Test
    @DisplayName("库存回补时点：未发货取消受理即补，退货退款签收后补")
    void restockTiming() {
        assertTrue(RefundPolicy.restockOnAccept(RefundType.CANCEL_BEFORE_SHIP.getType()));
        assertFalse(RefundPolicy.restockOnAccept(RefundType.RETURN_AND_REFUND.getType()));

        assertTrue(RefundPolicy.restockOnConfirmReturn(RefundType.RETURN_AND_REFUND.getType()));
        assertFalse(RefundPolicy.restockOnConfirmReturn(RefundType.CANCEL_BEFORE_SHIP.getType()));
    }
}
