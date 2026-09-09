package cc.ivera.bill.infrastructure.persistence.converter;

import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.infrastructure.persistence.po.BillImportPO;

/**
 * BillImport ↔ BillImportPO 转换（字段同名手写映射）。
 */
public final class BillImportPOConverter {

    private BillImportPOConverter() {
    }

    public static BillImportPO toPO(BillImport domain) {
        if (domain == null) {
            return null;
        }
        BillImportPO po = new BillImportPO();
        po.setId(domain.getId());
        po.setImportNo(domain.getImportNo());
        po.setChannelCode(domain.getChannelCode());
        po.setBillType(domain.getBillType());
        po.setBillKind(domain.getBillKind());
        po.setBillDate(domain.getBillDate());
        po.setFileName(domain.getFileName());
        po.setFileHash(domain.getFileHash());
        po.setTotalRecordCount(domain.getTotalRecordCount());
        po.setPayRecordCount(domain.getPayRecordCount());
        po.setRefundRecordCount(domain.getRefundRecordCount());
        po.setBadLineCount(domain.getBadLineCount());
        po.setMatchedCount(domain.getMatchedCount());
        po.setDiscrepancyCount(domain.getDiscrepancyCount());
        po.setStatus(domain.getStatus());
        po.setReconcileTime(domain.getReconcileTime());
        po.setErrorMessage(domain.getErrorMessage());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static BillImport toDomain(BillImportPO po) {
        if (po == null) {
            return null;
        }
        BillImport domain = new BillImport();
        domain.setId(po.getId());
        domain.setImportNo(po.getImportNo());
        domain.setChannelCode(po.getChannelCode());
        domain.setBillType(po.getBillType());
        domain.setBillKind(po.getBillKind());
        domain.setBillDate(po.getBillDate());
        domain.setFileName(po.getFileName());
        domain.setFileHash(po.getFileHash());
        domain.setTotalRecordCount(po.getTotalRecordCount());
        domain.setPayRecordCount(po.getPayRecordCount());
        domain.setRefundRecordCount(po.getRefundRecordCount());
        domain.setBadLineCount(po.getBadLineCount());
        domain.setMatchedCount(po.getMatchedCount());
        domain.setDiscrepancyCount(po.getDiscrepancyCount());
        domain.setStatus(po.getStatus());
        domain.setReconcileTime(po.getReconcileTime());
        domain.setErrorMessage(po.getErrorMessage());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
