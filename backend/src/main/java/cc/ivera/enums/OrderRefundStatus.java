package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单退款汇总状态（V2 四维状态之一，落 t_order_info.refund_status）。
 */
@AllArgsConstructor
@Getter
public enum OrderRefundStatus {

    /** 无退款 */
    NONE("NONE"),

    /** 退款中（存在冻结中的售后申请） */
    REFUNDING("REFUNDING"),

    /** 部分退款 */
    PARTIAL_REFUNDED("PARTIAL_REFUNDED"),

    /** 全额退款 */
    FULL_REFUNDED("FULL_REFUNDED");

    /**
     * 类型
     */
    private final String type;
}
