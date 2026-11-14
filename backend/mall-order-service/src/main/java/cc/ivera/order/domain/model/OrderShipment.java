package cc.ivera.order.domain.model;

import cc.ivera.order.domain.enums.ShipmentStatus;
import cc.ivera.shared.domain.exception.BizException;
import lombok.Data;

import java.util.Date;

/**
 * 物流单聚合根（t_order_shipment）：通过物流网关对接（当前为模拟物流商）。
 * 状态机：SHIPPED → IN_TRANSIT → DELIVERED → RECEIVED（/CANCELLED）；
 * 状态迁移的并发权威闸门为仓储 CAS（按运单号 + 状态条件），定时任务模拟推进，天然幂等。
 */
@Data
public class OrderShipment {

    private Long id;

    private String shipmentNo;//物流单编号

    private String orderNo;//商户订单编号

    private String logisticsCompany;//物流公司（模拟：模拟快递）

    private String trackingNo;//运单号（模拟物流商生成）

    private String status;//SHIPPED/IN_TRANSIT/DELIVERED/RECEIVED/CANCELLED

    private Date shippedTime;//发货时间

    private Date inTransitTime;//进入运输时间（模拟）

    private Date deliveredTime;//派送送达时间（模拟）

    private Date receivedTime;//用户确认收货时间

    private String remark;//备注

    private Date createTime;

    private Date updateTime;

    /**
     * 发货建单工厂：物流商运单号由网关创建后传入，初始 SHIPPED。
     */
    public static OrderShipment createShipped(String shipmentNo, String orderNo, String trackingNo, Date now) {
        OrderShipment shipment = new OrderShipment();
        shipment.setShipmentNo(shipmentNo);
        shipment.setOrderNo(orderNo);
        shipment.setLogisticsCompany("模拟快递");
        shipment.setTrackingNo(trackingNo);
        shipment.setStatus(ShipmentStatus.SHIPPED.getType());
        shipment.setShippedTime(now);
        shipment.setRemark("模拟物流商发货");
        return shipment;
    }

    public boolean isReceived() {
        return ShipmentStatus.RECEIVED.getType().equals(status);
    }

    /**
     * 确认收货前置守卫：仅物流送达（DELIVERED）后允许签收；已发货/运输中不可提前签收。
     * 已 RECEIVED 的幂等返回由应用层判断（{@link #isReceived()}）。
     */
    public void requireDeliveredForReceipt(String orderNo) {
        if (!ShipmentStatus.DELIVERED.getType().equals(status)) {
            throw new BizException("物流尚未送达，暂不能确认收货，orderNo=" + orderNo);
        }
    }
}
