package cc.ivera.bill.infrastructure.persistence.converter;

import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.infrastructure.persistence.po.BillRecordPO;

/**
 * BillRecord ↔ BillRecordPO 转换（字段同名手写映射）。
 */
public final class BillRecordPOConverter {

    private BillRecordPOConverter() {
    }

    public static BillRecordPO toPO(BillRecord domain) {
        if (domain == null) {
            return null;
        }
        BillRecordPO po = new BillRecordPO();
        po.setId(domain.getId());
        po.setImportId(domain.getImportId());
        po.setChannelCode(domain.getChannelCode());
        po.setBillDate(domain.getBillDate());
        po.setRecordType(domain.getRecordType());
        po.setChannelSerialNo(domain.getChannelSerialNo());
        po.setBizNo(domain.getBizNo());
        po.setTradeType(domain.getTradeType());
        po.setTradeStatus(domain.getTradeStatus());
        po.setTotalAmount(domain.getTotalAmount());
        po.setRefundAmount(domain.getRefundAmount());
        po.setTradeTime(domain.getTradeTime());
        po.setRefundApplyTime(domain.getRefundApplyTime());
        po.setRefundSuccessTime(domain.getRefundSuccessTime());
        po.setRawLine(domain.getRawLine());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static BillRecord toDomain(BillRecordPO po) {
        if (po == null) {
            return null;
        }
        BillRecord domain = new BillRecord();
        domain.setId(po.getId());
        domain.setImportId(po.getImportId());
        domain.setChannelCode(po.getChannelCode());
        domain.setBillDate(po.getBillDate());
        domain.setRecordType(po.getRecordType());
        domain.setChannelSerialNo(po.getChannelSerialNo());
        domain.setBizNo(po.getBizNo());
        domain.setTradeType(po.getTradeType());
        domain.setTradeStatus(po.getTradeStatus());
        domain.setTotalAmount(po.getTotalAmount());
        domain.setRefundAmount(po.getRefundAmount());
        domain.setTradeTime(po.getTradeTime());
        domain.setRefundApplyTime(po.getRefundApplyTime());
        domain.setRefundSuccessTime(po.getRefundSuccessTime());
        domain.setRawLine(po.getRawLine());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
