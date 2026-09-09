package cc.ivera.order.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单交易生命周期状态（V2 四维状态之一，落 t_order_info.order_status）。
 * 旧 V1 单一状态值保留在 legacy_status 仅供审计。
 */
@AllArgsConstructor
@Getter
public enum OrderLifecycleStatus {

    /**
     * 待支付
     */
    WAIT_PAY("WAIT_PAY"),

    /**
     * 有效（已支付，交易进行中）
     */
    ACTIVE("ACTIVE"),

    /**
     * 已关闭（超时未支付关闭/取消）
     */
    CLOSED("CLOSED"),

    /**
     * 已完成（履约收货且无进行中售后）
     */
    COMPLETED("COMPLETED");

    /**
     * 类型
     */
    private final String type;
}
