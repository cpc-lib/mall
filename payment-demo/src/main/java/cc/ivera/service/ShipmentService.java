package cc.ivera.service;

import cc.ivera.entity.OrderShipment;

/**
 * 履约/物流域服务（V2）。
 * 状态机：SHIPPED → IN_TRANSIT → DELIVERED → RECEIVED；
 * 与退款防线联动：只有 WAIT_SHIP 允许未发货取消，SHIPPED 后仅退款/退货退款。
 */
public interface ShipmentService {

    /**
     * 管理员发货：CAS 订单 fulfillment WAIT_SHIP→SHIPPED + 创建物流单。
     * 幂等约束：同一订单重复发货报错；非 WAIT_SHIP 或未支付订单拒绝。
     */
    OrderShipment ship(String orderNo);

    /**
     * 用户确认收货：物流单 CAS DELIVERED→RECEIVED + 订单 fulfillment SHIPPED→RECEIVED。
     */
    void confirmReceipt(String orderNo);

    /**
     * 查询订单物流详情（无物流单返回 null）。
     */
    OrderShipment getShipment(String orderNo);

    /**
     * 模拟物流推进（定时任务调用，幂等）：
     * SHIPPED（超 in-transit 延迟）→ IN_TRANSIT →（超 deliver 延迟）→ DELIVERED。
     */
    void advanceMockLogistics();
}
