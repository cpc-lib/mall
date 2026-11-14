package cc.ivera.order.infrastructure.persistence.converter;

import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.infrastructure.persistence.po.OrderItemPO;

/**
 * OrderItem ↔ OrderItemPO 转换。
 */
public final class OrderItemPOConverter {

    private OrderItemPOConverter() {
    }

    public static OrderItemPO toPO(OrderItem domain) {
        if (domain == null) {
            return null;
        }
        OrderItemPO po = new OrderItemPO();
        po.setId(domain.getId());
        po.setOrderId(domain.getOrderId());
        po.setOrderNo(domain.getOrderNo());
        po.setProductId(domain.getProductId());
        po.setProductTitle(domain.getProductTitle());
        po.setUnitPrice(domain.getUnitPrice());
        po.setQuantity(domain.getQuantity());
        po.setDealUnitAmount(domain.getDealUnitAmount());
        po.setOriginalTotalAmount(domain.getOriginalTotalAmount());
        po.setDiscountAmount(domain.getDiscountAmount());
        po.setPayAmount(domain.getPayAmount());
        po.setRefundedQty(domain.getRefundedQty());
        po.setRefundFrozenQty(domain.getRefundFrozenQty());
        po.setRefundFrozenAmount(domain.getRefundFrozenAmount());
        po.setRefundedAmount(domain.getRefundedAmount());
        po.setRestockedQty(domain.getRestockedQty());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static OrderItem toDomain(OrderItemPO po) {
        if (po == null) {
            return null;
        }
        OrderItem domain = new OrderItem();
        domain.setId(po.getId());
        domain.setOrderId(po.getOrderId());
        domain.setOrderNo(po.getOrderNo());
        domain.setProductId(po.getProductId());
        domain.setProductTitle(po.getProductTitle());
        domain.setUnitPrice(po.getUnitPrice());
        domain.setQuantity(po.getQuantity());
        domain.setDealUnitAmount(po.getDealUnitAmount());
        domain.setOriginalTotalAmount(po.getOriginalTotalAmount());
        domain.setDiscountAmount(po.getDiscountAmount());
        domain.setPayAmount(po.getPayAmount());
        domain.setRefundedQty(po.getRefundedQty());
        domain.setRefundFrozenQty(po.getRefundFrozenQty());
        domain.setRefundFrozenAmount(po.getRefundFrozenAmount());
        domain.setRefundedAmount(po.getRefundedAmount());
        domain.setRestockedQty(po.getRestockedQty());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
