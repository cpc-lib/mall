package cc.ivera.order.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 履约状态（V2 四维状态之一，落 t_order_info.fulfillment_status）。
 * 是退款类型合法性的硬校验依据：WAIT_SHIP 才允许未发货取消。
 */
@AllArgsConstructor
@Getter
public enum FulfillmentStatus {

    /**
     * 待发货
     */
    WAIT_SHIP("WAIT_SHIP"),

    /**
     * 已发货
     */
    SHIPPED("SHIPPED"),

    /**
     * 已收货
     */
    RECEIVED("RECEIVED"),

    /**
     * 已取消（未发货取消履约终止）
     */
    CANCELLED("CANCELLED");

    /**
     * 类型
     */
    private final String type;
}
