package cc.ivera.product.infrastructure.persistence.converter;

import cc.ivera.product.domain.model.InventoryReservation;
import cc.ivera.product.infrastructure.persistence.po.InventoryReservationPO;

/**
 * InventoryReservation 记录 ↔ PO 转换。
 */
public final class InventoryReservationPOConverter {

    private InventoryReservationPOConverter() {
    }

    public static InventoryReservationPO toPO(InventoryReservation domain) {
        if (domain == null) {
            return null;
        }
        InventoryReservationPO po = new InventoryReservationPO();
        po.setId(domain.getId());
        po.setReservationNo(domain.getReservationNo());
        po.setOrderNo(domain.getOrderNo());
        po.setOrderItemId(domain.getOrderItemId());
        po.setProductId(domain.getProductId());
        po.setQuantity(domain.getQuantity());
        po.setStatus(domain.getStatus());
        po.setExpireTime(domain.getExpireTime());
        po.setCommitTime(domain.getCommitTime());
        po.setReleaseTime(domain.getReleaseTime());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static InventoryReservation toDomain(InventoryReservationPO po) {
        if (po == null) {
            return null;
        }
        InventoryReservation domain = new InventoryReservation();
        domain.setId(po.getId());
        domain.setReservationNo(po.getReservationNo());
        domain.setOrderNo(po.getOrderNo());
        domain.setOrderItemId(po.getOrderItemId());
        domain.setProductId(po.getProductId());
        domain.setQuantity(po.getQuantity());
        domain.setStatus(po.getStatus());
        domain.setExpireTime(po.getExpireTime());
        domain.setCommitTime(po.getCommitTime());
        domain.setReleaseTime(po.getReleaseTime());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
