package cc.ivera.refund.domain.policy;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.refund.domain.enums.RefundType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RefundPolicy 纯域策略单测（无副作用，不连数据库）：尾差分摊、可退额度、补库类型判定。
 */
class RefundPolicyTest {

    private OrderItem item(int quantity, int payAmount, Integer refundedQty, Integer refundedAmount,
                           Integer frozenQty, Integer frozenAmount) {
        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setQuantity(quantity);
        item.setPayAmount(payAmount);
        item.setRefundedQty(refundedQty);
        item.setRefundedAmount(refundedAmount);
        item.setRefundFrozenQty(frozenQty);
        item.setRefundFrozenAmount(frozenAmount);
        return item;
    }

    @Test
    void refundableQtySubtractsRefundedAndFrozen() {
        assertEquals(2, RefundPolicy.refundableQty(item(5, 5000, 2, 2000, 1, 1000)));
        // 已退+冻结超过购买数时钳为 0
        assertEquals(0, RefundPolicy.refundableQty(item(3, 3000, 2, 2000, 2, 2000)));
        // 历史空值按 0 处理
        assertEquals(3, RefundPolicy.refundableQty(item(3, 3000, null, null, null, null)));
    }

    @Test
    void refundableAmountSubtractsRefundedAndFrozen() {
        assertEquals(3500, RefundPolicy.refundableAmount(item(5, 5000, 1, 1000, 1, 500)));
        assertEquals(0, RefundPolicy.refundableAmount(item(1, 1000, 1, 1000, 0, 0)));
    }

    @Test
    void orderRefundableAmountSubtractsRefundedAndFrozen() {
        OrderInfo order = new OrderInfo();
        order.setPaidAmount(10000);
        order.setRefundedAmount(3000);
        order.setRefundFrozenAmount(1000);
        assertEquals(6000, RefundPolicy.orderRefundableAmount(order));

        OrderInfo exhausted = new OrderInfo();
        exhausted.setPaidAmount(1000);
        exhausted.setRefundedAmount(1000);
        exhausted.setRefundFrozenAmount(0);
        assertEquals(0, RefundPolicy.orderRefundableAmount(exhausted));
    }

    @Test
    void nonLastPieceUsesFloorEvenShare() {
        // 1000 分 / 3 件 = 333.3，floor 均摊 333/件
        OrderItem item = item(3, 1000, 0, 0, 0, 0);
        assertEquals(333, RefundPolicy.calcRefundAmount(item, 1));
        assertEquals(666, RefundPolicy.calcRefundAmount(item, 2));
    }

    @Test
    void lastPieceEatsTailDifference() {
        // 已退 2 件共 666（两次 floor 均摊），最后 1 件吃掉尾差 1000-666=334
        OrderItem item = item(3, 1000, 2, 666, 0, 0);
        assertEquals(334, RefundPolicy.calcRefundAmount(item, 1));
    }

    @Test
    void calcRefundAmountRejectsInvalidQuantity() {
        assertThrows(IllegalArgumentException.class,
            () -> RefundPolicy.calcRefundAmount(item(3, 1000, 0, 0, 0, 0), 0));
        assertThrows(IllegalArgumentException.class,
            () -> RefundPolicy.calcRefundAmount(item(3, 1000, 0, 0, 0, 0), 4));
        assertThrows(IllegalArgumentException.class,
            () -> RefundPolicy.calcRefundAmount(item(0, 0, null, null, null, null), 1));
    }

    @Test
    void requireRefundInvariantDetectsOverflow() {
        // 数量维度超额：已退+冻结 > 购买数
        assertThrows(IllegalStateException.class,
            () -> RefundPolicy.requireRefundInvariant(item(3, 3000, 2, 2000, 2, 2000)));
        // 金额维度超额：已退+冻结 > 实付
        assertThrows(IllegalStateException.class,
            () -> RefundPolicy.requireRefundInvariant(item(3, 3000, 1, 2000, 0, 1500)));
        // 边界内不抛
        RefundPolicy.requireRefundInvariant(item(3, 3000, 1, 1000, 1, 1000));
    }

    @Test
    void restockOnAcceptOnlyForCancelBeforeShip() {
        assertTrue(RefundPolicy.restockOnAccept(RefundType.CANCEL_BEFORE_SHIP.getType()));
        assertFalse(RefundPolicy.restockOnAccept(RefundType.RETURN_AND_REFUND.getType()));
        assertFalse(RefundPolicy.restockOnAccept(RefundType.REFUND_ONLY.getType()));
        assertFalse(RefundPolicy.restockOnAccept(RefundType.PRICE_ADJUSTMENT.getType()));
        assertFalse(RefundPolicy.restockOnAccept(RefundType.DUPLICATE_PAYMENT.getType()));
        assertFalse(RefundPolicy.restockOnAccept(RefundType.LATE_PAYMENT.getType()));
    }

    @Test
    void restockOnConfirmReturnOnlyForReturnAndRefund() {
        assertTrue(RefundPolicy.restockOnConfirmReturn(RefundType.RETURN_AND_REFUND.getType()));
        assertFalse(RefundPolicy.restockOnConfirmReturn(RefundType.CANCEL_BEFORE_SHIP.getType()));
        assertFalse(RefundPolicy.restockOnConfirmReturn(RefundType.REFUND_ONLY.getType()));
        assertFalse(RefundPolicy.restockOnConfirmReturn(RefundType.PRICE_ADJUSTMENT.getType()));
        assertFalse(RefundPolicy.restockOnConfirmReturn(RefundType.DUPLICATE_PAYMENT.getType()));
        assertFalse(RefundPolicy.restockOnConfirmReturn(RefundType.LATE_PAYMENT.getType()));
    }
}
