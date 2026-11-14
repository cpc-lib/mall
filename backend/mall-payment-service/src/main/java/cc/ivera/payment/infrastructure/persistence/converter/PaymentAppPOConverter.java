package cc.ivera.payment.infrastructure.persistence.converter;

import cc.ivera.payment.domain.model.PaymentApp;
import cc.ivera.payment.infrastructure.persistence.po.PaymentAppPO;

/**
 * PaymentApp ↔ PaymentAppPO 转换（字段同名手写映射）。
 */
public final class PaymentAppPOConverter {

    private PaymentAppPOConverter() {
    }

    public static PaymentAppPO toPO(PaymentApp domain) {
        if (domain == null) {
            return null;
        }
        PaymentAppPO po = new PaymentAppPO();
        po.setId(domain.getId());
        po.setAppName(domain.getAppName());
        po.setAppCode(domain.getAppCode());
        po.setAppStatus(domain.getAppStatus());
        po.setChannelId(domain.getChannelId());
        po.setAppDesc(domain.getAppDesc());
        po.setSortOrder(domain.getSortOrder());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PaymentApp toDomain(PaymentAppPO po) {
        if (po == null) {
            return null;
        }
        PaymentApp domain = new PaymentApp();
        domain.setId(po.getId());
        domain.setAppName(po.getAppName());
        domain.setAppCode(po.getAppCode());
        domain.setAppStatus(po.getAppStatus());
        domain.setChannelId(po.getChannelId());
        domain.setAppDesc(po.getAppDesc());
        domain.setSortOrder(po.getSortOrder());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
