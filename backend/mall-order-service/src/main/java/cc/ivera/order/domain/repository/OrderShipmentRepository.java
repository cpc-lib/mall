package cc.ivera.order.domain.repository;

import cc.ivera.order.domain.model.OrderShipment;

import java.util.Date;
import java.util.List;

/**
 * 物流单聚合仓储端口。状态迁移一律走 casTransition 条件 UPDATE。
 */
public interface OrderShipmentRepository {

    /**
     * 新建物流单（id 回填）。
     */
    void save(OrderShipment shipment);

    /**
     * 查询订单最新一笔物流单（id 最大），无则 null。
     */
    OrderShipment findLatestByOrderNo(String orderNo);

    /**
     * 批量查询多订单物流单（id 倒序），由调用方按订单归并最新状态。
     */
    List<OrderShipment> listByOrderNos(List<String> orderNos);

    /**
     * 已发货且发货时间早于 cutoff 的运单（SHIPPED → IN_TRANSIT 模拟推进候选）。
     */
    List<OrderShipment> listShippedBefore(Date cutoff);

    /**
     * 运输中且进入运输时间早于 cutoff 的运单（IN_TRANSIT → DELIVERED 模拟推进候选）。
     */
    List<OrderShipment> listInTransitBefore(Date cutoff);

    /**
     * 物流状态 CAS 迁移（按运单号 + 当前状态），同时落对应时间列为当前时间。
     * timeColumn：shippedTime/inTransitTime/deliveredTime/receivedTime。
     *
     * @return 受影响行数（0 表示状态已被并发变更）
     */
    int casTransition(String trackingNo, String from, String to, String timeColumn);
}
