package cc.ivera.refund.infrastructure.persistence.converter;

import cc.ivera.refund.domain.model.RefundItem;
import cc.ivera.refund.infrastructure.persistence.po.RefundItemPO;

/**
 * RefundItem ↔ RefundItemPO 转换（字段同名手写映射）。
 */
public final class RefundItemPOConverter {

    private RefundItemPOConverter() {
    }

    public static RefundItemPO toPO(RefundItem domain) {
        if (domain == null) {
            return null;
        }
        RefundItemPO po = new RefundItemPO();
        po.setId(domain.getId());
        po.setRefundOrderId(domain.getRefundOrderId());
        po.setRefundNo(domain.getRefundNo());
        po.setOrderItemId(domain.getOrderItemId());
        po.setProductId(domain.getProductId());
        po.setUnitPrice(domain.getUnitPrice());
        po.setRefundQty(domain.getRefundQty());
        po.setRefundAmount(domain.getRefundAmount());
        po.setRestockQty(domain.getRestockQty());
        po.setLegacyStockReturned(domain.getLegacyStockReturned());
        po.setStatus(domain.getStatus());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static RefundItem toDomain(RefundItemPO po) {
        if (po == null) {
            return null;
        }
        RefundItem domain = new RefundItem();
        domain.setId(po.getId());
        domain.setRefundOrderId(po.getRefundOrderId());
        domain.setRefundNo(po.getRefundNo());
        domain.setOrderItemId(po.getOrderItemId());
        domain.setProductId(po.getProductId());
        domain.setUnitPrice(po.getUnitPrice());
        domain.setRefundQty(po.getRefundQty());
        domain.setRefundAmount(po.getRefundAmount());
        domain.setRestockQty(po.getRestockQty());
        domain.setLegacyStockReturned(po.getLegacyStockReturned());
        domain.setStatus(po.getStatus());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
