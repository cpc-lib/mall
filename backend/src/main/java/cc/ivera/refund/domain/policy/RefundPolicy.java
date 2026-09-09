package cc.ivera.refund.domain.policy;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.refund.domain.enums.RefundType;

/**
 * 退款纯域策略（V2，无副作用）：
 * - 三层可退额度：订单金额 / 明细金额+数量 / 支付单渠道资金；
 * - §8 尾差规则：非最后一件按 floor(行实付/购买数量)*退货数量 计算，
 * 退完剩余全部数量（最后一件）时直接给剩余全部可退金额，保证累计退款=行实付；
 * - §37 补库存策略：按退款类型决定回补时点（受理即补 / 签收后补 / 不补）。
 */
public final class RefundPolicy {
    private RefundPolicy() {
    }

    /**
     * 明细剩余可退数量（不含冻结，冻结中数量不可再次申请）
     */
    public static int refundableQty(OrderItem item) {
        return Math.max(item.getQuantity() - nvl(item.getRefundedQty()) - nvl(item.getRefundFrozenQty()), 0);
    }

    /**
     * 明细剩余可退金额（不含冻结）
     */
    public static int refundableAmount(OrderItem item) {
        return Math.max(nvl(item.getPayAmount()) - nvl(item.getRefundedAmount()) - nvl(item.getRefundFrozenAmount()), 0);
    }

    /**
     * 订单剩余可退金额（订单层防线）
     */
    public static int orderRefundableAmount(OrderInfo order) {
        return Math.max(nvl(order.getPaidAmount()) - nvl(order.getRefundedAmount()) - nvl(order.getRefundFrozenAmount()), 0);
    }

    /**
     * 按尾差规则计算本次退款金额（分）。
     * 规则：refundQty == 剩余可退数量（最后一件）→ 剩余全部可退金额；
     * 否则 → floor(payAmount / quantity) * refundQty。
     *
     * @throws IllegalArgumentException 数量非法或超过可退数量
     */
    public static int calcRefundAmount(OrderItem item, int refundQty) {
        int quantity = item.getQuantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("订单明细数量非法，orderItemId=" + item.getId());
        }
        int refundableQty = refundableQty(item);
        if (refundQty <= 0) {
            throw new IllegalArgumentException("退款数量必须大于0，orderItemId=" + item.getId());
        }
        if (refundQty > refundableQty) {
            throw new IllegalArgumentException("退款数量超过可退数量，orderItemId=" + item.getId()
                + "，可退=" + refundableQty);
        }
        if (refundQty == refundableQty) {
            // 最后一件吃尾差：剩余可退金额全额（含历史按件均摊留下的尾差）
            return refundableAmount(item);
        }
        return nvl(item.getPayAmount()) / quantity * refundQty;
    }

    /**
     * 不变量校验：已退+冻结 不得超过 实付（并发双退的最终防线由 DB 原子 SQL 保证）
     */
    public static void requireRefundInvariant(OrderItem item) {
        if (nvl(item.getRefundedQty()) + nvl(item.getRefundFrozenQty()) > item.getQuantity()
            || nvl(item.getRefundedAmount()) + nvl(item.getRefundFrozenAmount()) > nvl(item.getPayAmount())) {
            throw new IllegalStateException("退款额度不变量被破坏，orderItemId=" + item.getId());
        }
    }

    /**
     * §37 受理时即回补库存的退款类型（未发货取消：库存仍在锁定语义内，受理即归还）。
     */
    public static boolean restockOnAccept(String refundType) {
        return RefundType.CANCEL_BEFORE_SHIP.getType().equals(refundType);
    }

    /**
     * §37 管理员签收质检后回补库存的退款类型（退货退款：确认货物回仓才补）。
     */
    public static boolean restockOnConfirmReturn(String refundType) {
        return RefundType.RETURN_AND_REFUND.getType().equals(refundType);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
