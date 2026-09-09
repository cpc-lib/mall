package cc.ivera.user.infrastructure.persistence.converter;

import cc.ivera.user.domain.model.ShippingAddress;
import cc.ivera.user.infrastructure.persistence.po.ShippingAddressPO;

/**
 * ShippingAddress ↔ ShippingAddressPO 转换（字段同名手写映射）。
 */
public final class ShippingAddressPOConverter {

    private ShippingAddressPOConverter() {
    }

    public static ShippingAddressPO toPO(ShippingAddress domain) {
        if (domain == null) {
            return null;
        }
        ShippingAddressPO po = new ShippingAddressPO();
        po.setId(domain.getId());
        po.setUserId(domain.getUserId());
        po.setReceiverName(domain.getReceiverName());
        po.setReceiverPhone(domain.getReceiverPhone());
        po.setProvince(domain.getProvince());
        po.setCity(domain.getCity());
        po.setDistrict(domain.getDistrict());
        po.setDetail(domain.getDetail());
        po.setIsDefault(domain.getIsDefault());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static ShippingAddress toDomain(ShippingAddressPO po) {
        if (po == null) {
            return null;
        }
        ShippingAddress domain = new ShippingAddress();
        domain.setId(po.getId());
        domain.setUserId(po.getUserId());
        domain.setReceiverName(po.getReceiverName());
        domain.setReceiverPhone(po.getReceiverPhone());
        domain.setProvince(po.getProvince());
        domain.setCity(po.getCity());
        domain.setDistrict(po.getDistrict());
        domain.setDetail(po.getDetail());
        domain.setIsDefault(po.getIsDefault());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
