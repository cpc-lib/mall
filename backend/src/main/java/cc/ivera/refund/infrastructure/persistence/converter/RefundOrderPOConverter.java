package cc.ivera.refund.infrastructure.persistence.converter;

import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.infrastructure.persistence.po.RefundOrderPO;

/**
 * RefundOrder 聚合根 ↔ RefundOrderPO 转换（字段同名手写映射）。
 */
public final class RefundOrderPOConverter {

    private RefundOrderPOConverter() {
    }

    public static RefundOrderPO toPO(RefundOrder domain) {
        if (domain == null) {
            return null;
        }
        RefundOrderPO po = new RefundOrderPO();
        po.setId(domain.getId());
        po.setRefundNo(domain.getRefundNo());
        po.setOrderNo(domain.getOrderNo());
        po.setUserId(domain.getUserId());
        po.setRefundType(domain.getRefundType());
        po.setPaymentNo(domain.getPaymentNo());
        po.setRefundAmount(domain.getRefundAmount());
        po.setReason(domain.getReason());
        po.setStatus(domain.getStatus());
        po.setLegacyApplyStatus(domain.getLegacyApplyStatus());
        po.setApplyType(domain.getApplyType());
        po.setAdminRemark(domain.getAdminRemark());
        po.setAcceptedTime(domain.getAcceptedTime());
        po.setSuccessTime(domain.getSuccessTime());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static RefundOrder toDomain(RefundOrderPO po) {
        if (po == null) {
            return null;
        }
        RefundOrder domain = new RefundOrder();
        domain.setId(po.getId());
        domain.setRefundNo(po.getRefundNo());
        domain.setOrderNo(po.getOrderNo());
        domain.setUserId(po.getUserId());
        domain.setRefundType(po.getRefundType());
        domain.setPaymentNo(po.getPaymentNo());
        domain.setRefundAmount(po.getRefundAmount());
        domain.setReason(po.getReason());
        domain.setStatus(po.getStatus());
        domain.setLegacyApplyStatus(po.getLegacyApplyStatus());
        domain.setApplyType(po.getApplyType());
        domain.setAdminRemark(po.getAdminRemark());
        domain.setAcceptedTime(po.getAcceptedTime());
        domain.setSuccessTime(po.getSuccessTime());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
