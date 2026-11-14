package cc.ivera.payment.infrastructure.persistence.converter;

import cc.ivera.payment.domain.model.PaymentDeal;
import cc.ivera.payment.infrastructure.persistence.po.PaymentDealPO;

/**
 * PaymentDeal ↔ PaymentDealPO 转换（字段同名手写映射）。
 */
public final class PaymentDealPOConverter {

    private PaymentDealPOConverter() {
    }

    public static PaymentDealPO toPO(PaymentDeal domain) {
        if (domain == null) {
            return null;
        }
        PaymentDealPO po = new PaymentDealPO();
        po.setId(domain.getId());
        po.setPaymentNo(domain.getPaymentNo());
        po.setOrderNo(domain.getOrderNo());
        po.setChannel(domain.getChannel());
        po.setPaidAmount(domain.getPaidAmount());
        po.setPaidTime(domain.getPaidTime());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PaymentDeal toDomain(PaymentDealPO po) {
        if (po == null) {
            return null;
        }
        PaymentDeal domain = new PaymentDeal();
        domain.setId(po.getId());
        domain.setPaymentNo(po.getPaymentNo());
        domain.setOrderNo(po.getOrderNo());
        domain.setChannel(po.getChannel());
        domain.setPaidAmount(po.getPaidAmount());
        domain.setPaidTime(po.getPaidTime());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
