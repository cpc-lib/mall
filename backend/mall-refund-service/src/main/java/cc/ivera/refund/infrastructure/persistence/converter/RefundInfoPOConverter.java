package cc.ivera.refund.infrastructure.persistence.converter;

import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.infrastructure.persistence.po.RefundInfoPO;

/**
 * RefundInfo ↔ RefundInfoPO 转换（字段同名手写映射）。
 */
public final class RefundInfoPOConverter {

    private RefundInfoPOConverter() {
    }

    public static RefundInfoPO toPO(RefundInfo domain) {
        if (domain == null) {
            return null;
        }
        RefundInfoPO po = new RefundInfoPO();
        po.setId(domain.getId());
        po.setOrderNo(domain.getOrderNo());
        po.setRefundNo(domain.getRefundNo());
        po.setRefundId(domain.getRefundId());
        po.setTotalFee(domain.getTotalFee());
        po.setRefund(domain.getRefund());
        po.setReason(domain.getReason());
        po.setApprovalStatus(domain.getApprovalStatus());
        po.setApproveRemark(domain.getApproveRemark());
        po.setApprovedTime(domain.getApprovedTime());
        po.setRefundStatus(domain.getRefundStatus());
        po.setContentReturn(domain.getContentReturn());
        po.setContentNotify(domain.getContentNotify());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static RefundInfo toDomain(RefundInfoPO po) {
        if (po == null) {
            return null;
        }
        RefundInfo domain = new RefundInfo();
        domain.setId(po.getId());
        domain.setOrderNo(po.getOrderNo());
        domain.setRefundNo(po.getRefundNo());
        domain.setRefundId(po.getRefundId());
        domain.setTotalFee(po.getTotalFee());
        domain.setRefund(po.getRefund());
        domain.setReason(po.getReason());
        domain.setApprovalStatus(po.getApprovalStatus());
        domain.setApproveRemark(po.getApproveRemark());
        domain.setApprovedTime(po.getApprovedTime());
        domain.setRefundStatus(po.getRefundStatus());
        domain.setContentReturn(po.getContentReturn());
        domain.setContentNotify(po.getContentNotify());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
