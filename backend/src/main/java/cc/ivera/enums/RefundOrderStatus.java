package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 退款单状态（落 t_refund_order.status）。
 * 由 V1 t_refund_apply.apply_status（PENDING/ACCEPTED/...）演进。
 */
@AllArgsConstructor
@Getter
public enum RefundOrderStatus {

    /** 申请中（已冻结订单/明细额度） */
    APPLYING("APPLYING"),

    /** 已受理（等待渠道退款或退货签收） */
    APPROVED("APPROVED"),

    /** 已拒绝（释放冻结额度） */
    REJECTED("REJECTED"),

    /** 已撤回（用户主动撤销，释放冻结额度） */
    CANCELLED("CANCELLED"),

    /** 渠道退款中 */
    REFUNDING("REFUNDING"),

    /** 退款成功 */
    SUCCESS("SUCCESS"),

    /** 退款失败（可重试回 REFUNDING） */
    FAILED("FAILED");

    /**
     * 类型
     */
    private final String type;
}
