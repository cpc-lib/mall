package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 物流单状态（落 t_order_shipment.status）。
 * 状态机：SHIPPED → IN_TRANSIT → DELIVERED → RECEIVED（/CANCELLED）。
 */
@AllArgsConstructor
@Getter
public enum ShipmentStatus {

    /** 已发货 */
    SHIPPED("SHIPPED"),

    /** 运输中（模拟推进） */
    IN_TRANSIT("IN_TRANSIT"),

    /** 已派送（模拟推进） */
    DELIVERED("DELIVERED"),

    /** 已收货（用户确认收货） */
    RECEIVED("RECEIVED"),

    /** 已撤销 */
    CANCELLED("CANCELLED");

    /**
     * 类型
     */
    private final String type;
}
