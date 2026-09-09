package cc.ivera.order.infrastructure.persistence.converter;

import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.infrastructure.persistence.po.OrderShipmentPO;

/**
 * OrderShipment 聚合根 ↔ OrderShipmentPO 转换。
 */
public final class OrderShipmentPOConverter {

    private OrderShipmentPOConverter() {
    }

    public static OrderShipmentPO toPO(OrderShipment domain) {
        if (domain == null) {
            return null;
        }
        OrderShipmentPO po = new OrderShipmentPO();
        po.setId(domain.getId());
        po.setShipmentNo(domain.getShipmentNo());
        po.setOrderNo(domain.getOrderNo());
        po.setLogisticsCompany(domain.getLogisticsCompany());
        po.setTrackingNo(domain.getTrackingNo());
        po.setStatus(domain.getStatus());
        po.setShippedTime(domain.getShippedTime());
        po.setInTransitTime(domain.getInTransitTime());
        po.setDeliveredTime(domain.getDeliveredTime());
        po.setReceivedTime(domain.getReceivedTime());
        po.setRemark(domain.getRemark());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static OrderShipment toDomain(OrderShipmentPO po) {
        if (po == null) {
            return null;
        }
        OrderShipment domain = new OrderShipment();
        domain.setId(po.getId());
        domain.setShipmentNo(po.getShipmentNo());
        domain.setOrderNo(po.getOrderNo());
        domain.setLogisticsCompany(po.getLogisticsCompany());
        domain.setTrackingNo(po.getTrackingNo());
        domain.setStatus(po.getStatus());
        domain.setShippedTime(po.getShippedTime());
        domain.setInTransitTime(po.getInTransitTime());
        domain.setDeliveredTime(po.getDeliveredTime());
        domain.setReceivedTime(po.getReceivedTime());
        domain.setRemark(po.getRemark());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
