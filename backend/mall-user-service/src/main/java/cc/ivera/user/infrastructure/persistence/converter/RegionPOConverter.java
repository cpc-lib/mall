package cc.ivera.user.infrastructure.persistence.converter;

import cc.ivera.user.domain.model.Region;
import cc.ivera.user.infrastructure.persistence.po.RegionPO;

/**
 * Region ↔ RegionPO 转换（字段同名手写映射；Region 无审计时间字段）。
 */
public final class RegionPOConverter {

    private RegionPOConverter() {
    }

    public static RegionPO toPO(Region domain) {
        if (domain == null) {
            return null;
        }
        RegionPO po = new RegionPO();
        po.setId(domain.getId());
        po.setParentId(domain.getParentId());
        po.setCode(domain.getCode());
        po.setName(domain.getName());
        po.setLevel(domain.getLevel());
        po.setSort(domain.getSort());
        return po;
    }

    public static Region toDomain(RegionPO po) {
        if (po == null) {
            return null;
        }
        Region domain = new Region();
        domain.setId(po.getId());
        domain.setParentId(po.getParentId());
        domain.setCode(po.getCode());
        domain.setName(po.getName());
        domain.setLevel(po.getLevel());
        domain.setSort(po.getSort());
        return domain;
    }
}
