package cc.ivera.user.infrastructure.persistence.converter;

import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.infrastructure.persistence.po.PasswordResetRequestPO;

/**
 * PasswordResetRequest ↔ PasswordResetRequestPO 转换（字段同名手写映射）。
 */
public final class PasswordResetRequestPOConverter {

    private PasswordResetRequestPOConverter() {
    }

    public static PasswordResetRequestPO toPO(PasswordResetRequest domain) {
        if (domain == null) {
            return null;
        }
        PasswordResetRequestPO po = new PasswordResetRequestPO();
        po.setId(domain.getId());
        po.setUsername(domain.getUsername());
        po.setRemark(domain.getRemark());
        po.setStatus(domain.getStatus());
        po.setAdminRemark(domain.getAdminRemark());
        po.setHandledBy(domain.getHandledBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PasswordResetRequest toDomain(PasswordResetRequestPO po) {
        if (po == null) {
            return null;
        }
        PasswordResetRequest domain = new PasswordResetRequest();
        domain.setId(po.getId());
        domain.setUsername(po.getUsername());
        domain.setRemark(po.getRemark());
        domain.setStatus(po.getStatus());
        domain.setAdminRemark(po.getAdminRemark());
        domain.setHandledBy(po.getHandledBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
