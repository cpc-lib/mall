package cc.ivera.product.infrastructure.persistence.converter;

import cc.ivera.product.domain.model.InventoryTransaction;
import cc.ivera.product.infrastructure.persistence.po.InventoryTransactionPO;

/**
 * InventoryTransaction 记录 ↔ PO 转换。
 */
public final class InventoryTransactionPOConverter {

    private InventoryTransactionPOConverter() {
    }

    public static InventoryTransactionPO toPO(InventoryTransaction domain) {
        if (domain == null) {
            return null;
        }
        InventoryTransactionPO po = new InventoryTransactionPO();
        po.setId(domain.getId());
        po.setBizNo(domain.getBizNo());
        po.setBizType(domain.getBizType());
        po.setOrderNo(domain.getOrderNo());
        po.setOrderItemId(domain.getOrderItemId());
        po.setRefundNo(domain.getRefundNo());
        po.setProductId(domain.getProductId());
        po.setAvailableDelta(domain.getAvailableDelta());
        po.setLockedDelta(domain.getLockedDelta());
        po.setSoldDelta(domain.getSoldDelta());
        po.setLostDelta(domain.getLostDelta());
        po.setOperationStatus(domain.getOperationStatus());
        po.setErrorMessage(domain.getErrorMessage());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static InventoryTransaction toDomain(InventoryTransactionPO po) {
        if (po == null) {
            return null;
        }
        InventoryTransaction domain = new InventoryTransaction();
        domain.setId(po.getId());
        domain.setBizNo(po.getBizNo());
        domain.setBizType(po.getBizType());
        domain.setOrderNo(po.getOrderNo());
        domain.setOrderItemId(po.getOrderItemId());
        domain.setRefundNo(po.getRefundNo());
        domain.setProductId(po.getProductId());
        domain.setAvailableDelta(po.getAvailableDelta());
        domain.setLockedDelta(po.getLockedDelta());
        domain.setSoldDelta(po.getSoldDelta());
        domain.setLostDelta(po.getLostDelta());
        domain.setOperationStatus(po.getOperationStatus());
        domain.setErrorMessage(po.getErrorMessage());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
