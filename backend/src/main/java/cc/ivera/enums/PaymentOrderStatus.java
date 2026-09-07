package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付单状态（V2，落 t_payment_order.status）。
 * 一个业务订单允许多次支付尝试，但只允许一笔 SUCCESS 成交支付；
 * 多余成功支付进入 DUPLICATE_PAYMENT / LATE_PAYMENT 自动原路退款。
 */
@AllArgsConstructor
@Getter
public enum PaymentOrderStatus {

    /** 已创建（渠道下单前） */
    CREATED("CREATED"),

    /** 支付中（渠道下单成功，等待用户支付） */
    PAYING("PAYING"),

    /** 支付成功（有效成交支付） */
    SUCCESS("SUCCESS"),

    /** 已关闭（被新支付尝试替代/订单关闭） */
    CLOSED("CLOSED");

    /**
     * 类型
     */
    private final String type;
}
