package cc.ivera.user.infrastructure.persistence.converter;

import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.infrastructure.persistence.po.UserAccountPO;

/**
 * UserAccount ↔ UserAccountPO 转换（字段同名手写映射）。
 */
public final class UserAccountPOConverter {

    private UserAccountPOConverter() {
    }

    public static UserAccountPO toPO(UserAccount domain) {
        if (domain == null) {
            return null;
        }
        UserAccountPO po = new UserAccountPO();
        po.setId(domain.getId());
        po.setUsername(domain.getUsername());
        po.setPasswordHash(domain.getPasswordHash());
        po.setPasswordSalt(domain.getPasswordSalt());
        po.setRole(domain.getRole());
        po.setUserStatus(domain.getUserStatus());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static UserAccount toDomain(UserAccountPO po) {
        if (po == null) {
            return null;
        }
        UserAccount domain = new UserAccount();
        domain.setId(po.getId());
        domain.setUsername(po.getUsername());
        domain.setPasswordHash(po.getPasswordHash());
        domain.setPasswordSalt(po.getPasswordSalt());
        domain.setRole(po.getRole());
        domain.setUserStatus(po.getUserStatus());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
