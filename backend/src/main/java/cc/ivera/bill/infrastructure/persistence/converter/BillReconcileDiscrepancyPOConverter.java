package cc.ivera.bill.infrastructure.persistence.converter;

import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.infrastructure.persistence.po.BillReconcileDiscrepancyPO;

/**
 * BillReconcileDiscrepancy ↔ BillReconcileDiscrepancyPO 转换（字段同名手写映射）。
 */
public final class BillReconcileDiscrepancyPOConverter {

    private BillReconcileDiscrepancyPOConverter() {
    }

    public static BillReconcileDiscrepancyPO toPO(BillReconcileDiscrepancy domain) {
        if (domain == null) {
            return null;
        }
        BillReconcileDiscrepancyPO po = new BillReconcileDiscrepancyPO();
        po.setId(domain.getId());
        po.setImportId(domain.getImportId());
        po.setBillDate(domain.getBillDate());
        po.setBizType(domain.getBizType());
        po.setDiscrepancyType(domain.getDiscrepancyType());
        po.setBizNo(domain.getBizNo());
        po.setChannelSerialNo(domain.getChannelSerialNo());
        po.setLocalBizNo(domain.getLocalBizNo());
        po.setLocalLedgerNo(domain.getLocalLedgerNo());
        po.setLocalSerialNo(domain.getLocalSerialNo());
        po.setChannelAmount(domain.getChannelAmount());
        po.setLocalAmount(domain.getLocalAmount());
        po.setChannelStatus(domain.getChannelStatus());
        po.setLocalStatus(domain.getLocalStatus());
        po.setStatus(domain.getStatus());
        po.setResolveRemark(domain.getResolveRemark());
        po.setResolvedTime(domain.getResolvedTime());
        po.setResolvedBy(domain.getResolvedBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static BillReconcileDiscrepancy toDomain(BillReconcileDiscrepancyPO po) {
        if (po == null) {
            return null;
        }
        BillReconcileDiscrepancy domain = new BillReconcileDiscrepancy();
        domain.setId(po.getId());
        domain.setImportId(po.getImportId());
        domain.setBillDate(po.getBillDate());
        domain.setBizType(po.getBizType());
        domain.setDiscrepancyType(po.getDiscrepancyType());
        domain.setBizNo(po.getBizNo());
        domain.setChannelSerialNo(po.getChannelSerialNo());
        domain.setLocalBizNo(po.getLocalBizNo());
        domain.setLocalLedgerNo(po.getLocalLedgerNo());
        domain.setLocalSerialNo(po.getLocalSerialNo());
        domain.setChannelAmount(po.getChannelAmount());
        domain.setLocalAmount(po.getLocalAmount());
        domain.setChannelStatus(po.getChannelStatus());
        domain.setLocalStatus(po.getLocalStatus());
        domain.setStatus(po.getStatus());
        domain.setResolveRemark(po.getResolveRemark());
        domain.setResolvedTime(po.getResolvedTime());
        domain.setResolvedBy(po.getResolvedBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
