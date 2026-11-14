package cc.ivera.payment.infrastructure.persistence.converter;

import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.infrastructure.persistence.po.PaymentOrderPO;

/**
 * PaymentOrder 聚合根 ↔ PaymentOrderPO 转换（字段同名手写映射）。
 */
public final class PaymentOrderPOConverter {

    private PaymentOrderPOConverter() {
    }

    public static PaymentOrderPO toPO(PaymentOrder domain) {
        if (domain == null) {
            return null;
        }
        PaymentOrderPO po = new PaymentOrderPO();
        po.setId(domain.getId());
        po.setPaymentNo(domain.getPaymentNo());
        po.setOrderNo(domain.getOrderNo());
        po.setChannel(domain.getChannel());
        po.setChannelOrderNo(domain.getChannelOrderNo());
        po.setCodeUrl(domain.getCodeUrl());
        po.setRequestAmount(domain.getRequestAmount());
        po.setPaidAmount(domain.getPaidAmount());
        po.setRefundFrozenAmount(domain.getRefundFrozenAmount());
        po.setRefundedAmount(domain.getRefundedAmount());
        po.setStatus(domain.getStatus());
        po.setExpireTime(domain.getExpireTime());
        po.setPaidTime(domain.getPaidTime());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PaymentOrder toDomain(PaymentOrderPO po) {
        if (po == null) {
            return null;
        }
        PaymentOrder domain = new PaymentOrder();
        domain.setId(po.getId());
        domain.setPaymentNo(po.getPaymentNo());
        domain.setOrderNo(po.getOrderNo());
        domain.setChannel(po.getChannel());
        domain.setChannelOrderNo(po.getChannelOrderNo());
        domain.setCodeUrl(po.getCodeUrl());
        domain.setRequestAmount(po.getRequestAmount());
        domain.setPaidAmount(po.getPaidAmount());
        domain.setRefundFrozenAmount(po.getRefundFrozenAmount());
        domain.setRefundedAmount(po.getRefundedAmount());
        domain.setStatus(po.getStatus());
        domain.setExpireTime(po.getExpireTime());
        domain.setPaidTime(po.getPaidTime());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
