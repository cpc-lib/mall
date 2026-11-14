package cc.ivera.payment.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付状态（V2 四维状态之一，落 t_order_info.pay_status）。
 */
@AllArgsConstructor
@Getter
public enum PayStatus {

    /**
     * 未支付
     */
    UNPAID("UNPAID"),

    /**
     * 支付中（渠道已下单、二维码已生成，等待用户扫码确认）
     */
    PAYING("PAYING"),

    /**
     * 已支付
     */
    PAID("PAID");

    /**
     * 类型
     */
    private final String type;
}
