package cc.ivera.payment.infrastructure.persistence.converter;

import cc.ivera.payment.domain.model.PaymentInfo;
import cc.ivera.payment.infrastructure.persistence.po.PaymentInfoPO;

/**
 * PaymentInfo ↔ PaymentInfoPO 转换（字段同名手写映射）。
 */
public final class PaymentInfoPOConverter {

    private PaymentInfoPOConverter() {
    }

    public static PaymentInfoPO toPO(PaymentInfo domain) {
        if (domain == null) {
            return null;
        }
        PaymentInfoPO po = new PaymentInfoPO();
        po.setId(domain.getId());
        po.setOrderNo(domain.getOrderNo());
        po.setTransactionId(domain.getTransactionId());
        po.setPaymentType(domain.getPaymentType());
        po.setTradeType(domain.getTradeType());
        po.setTradeState(domain.getTradeState());
        po.setPayerTotal(domain.getPayerTotal());
        po.setContent(domain.getContent());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PaymentInfo toDomain(PaymentInfoPO po) {
        if (po == null) {
            return null;
        }
        PaymentInfo domain = new PaymentInfo();
        domain.setId(po.getId());
        domain.setOrderNo(po.getOrderNo());
        domain.setTransactionId(po.getTransactionId());
        domain.setPaymentType(po.getPaymentType());
        domain.setTradeType(po.getTradeType());
        domain.setTradeState(po.getTradeState());
        domain.setPayerTotal(po.getPayerTotal());
        domain.setContent(po.getContent());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
