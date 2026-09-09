package cc.ivera.bill.infrastructure.persistence.converter;

import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.infrastructure.persistence.po.BillImportPO;
import cc.ivera.bill.infrastructure.persistence.po.BillReconcileDiscrepancyPO;
import cc.ivera.bill.infrastructure.persistence.po.BillRecordPO;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * bill 上下文 PO ↔ 领域对象手写 Converter 往返映射测试：全字段等值，不连数据库。
 */
class BillPOConvertersTest {

    @Test
    void billImportRoundtripMapsAllFields() {
        BillImport domain = new BillImport();
        domain.setId(1L);
        domain.setImportNo("BILL20270506001");
        domain.setChannelCode("WXPAY");
        domain.setBillType("TRADE");
        domain.setBillKind("ALL");
        domain.setBillDate("2027-05-06");
        domain.setFileName("wxbill.csv");
        domain.setFileHash("hash-abc");
        domain.setTotalRecordCount(10);
        domain.setPayRecordCount(7);
        domain.setRefundRecordCount(3);
        domain.setBadLineCount(1);
        domain.setMatchedCount(8);
        domain.setDiscrepancyCount(2);
        domain.setStatus("RECONCILED");
        domain.setReconcileTime(new Date(7_000L));
        domain.setErrorMessage("err");
        domain.setCreateTime(new Date(1_000L));
        domain.setUpdateTime(new Date(2_000L));

        BillImport back = BillImportPOConverter.toDomain(BillImportPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getImportNo(), back.getImportNo());
        assertEquals(domain.getChannelCode(), back.getChannelCode());
        assertEquals(domain.getBillType(), back.getBillType());
        assertEquals(domain.getBillKind(), back.getBillKind());
        assertEquals(domain.getBillDate(), back.getBillDate());
        assertEquals(domain.getFileName(), back.getFileName());
        assertEquals(domain.getFileHash(), back.getFileHash());
        assertEquals(domain.getTotalRecordCount(), back.getTotalRecordCount());
        assertEquals(domain.getPayRecordCount(), back.getPayRecordCount());
        assertEquals(domain.getRefundRecordCount(), back.getRefundRecordCount());
        assertEquals(domain.getBadLineCount(), back.getBadLineCount());
        assertEquals(domain.getMatchedCount(), back.getMatchedCount());
        assertEquals(domain.getDiscrepancyCount(), back.getDiscrepancyCount());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getReconcileTime(), back.getReconcileTime());
        assertEquals(domain.getErrorMessage(), back.getErrorMessage());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void billImportConverterNullSafe() {
        assertNull(BillImportPOConverter.toPO(null));
        assertNull(BillImportPOConverter.toDomain((BillImportPO) null));
    }

    @Test
    void billRecordRoundtripMapsAllFields() {
        BillRecord domain = new BillRecord();
        domain.setId(2L);
        domain.setImportId(1L);
        domain.setChannelCode("WXPAY");
        domain.setBillDate("2027-05-06");
        domain.setRecordType("PAY");
        domain.setChannelSerialNo("WX123");
        domain.setBizNo("MCH456");
        domain.setTradeType("JSAPI");
        domain.setTradeStatus("SUCCESS");
        domain.setTotalAmount(199);
        domain.setRefundAmount(50);
        domain.setTradeTime(new Date(3_000L));
        domain.setRefundApplyTime(new Date(4_000L));
        domain.setRefundSuccessTime(new Date(5_000L));
        domain.setRawLine("`2027-05-06 10:00:00,...");
        domain.setCreateTime(new Date(1_000L));
        domain.setUpdateTime(new Date(2_000L));

        BillRecord back = BillRecordPOConverter.toDomain(BillRecordPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getImportId(), back.getImportId());
        assertEquals(domain.getChannelCode(), back.getChannelCode());
        assertEquals(domain.getBillDate(), back.getBillDate());
        assertEquals(domain.getRecordType(), back.getRecordType());
        assertEquals(domain.getChannelSerialNo(), back.getChannelSerialNo());
        assertEquals(domain.getBizNo(), back.getBizNo());
        assertEquals(domain.getTradeType(), back.getTradeType());
        assertEquals(domain.getTradeStatus(), back.getTradeStatus());
        assertEquals(domain.getTotalAmount(), back.getTotalAmount());
        assertEquals(domain.getRefundAmount(), back.getRefundAmount());
        assertEquals(domain.getTradeTime(), back.getTradeTime());
        assertEquals(domain.getRefundApplyTime(), back.getRefundApplyTime());
        assertEquals(domain.getRefundSuccessTime(), back.getRefundSuccessTime());
        assertEquals(domain.getRawLine(), back.getRawLine());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void billRecordConverterNullSafe() {
        assertNull(BillRecordPOConverter.toPO(null));
        assertNull(BillRecordPOConverter.toDomain((BillRecordPO) null));
    }

    @Test
    void billReconcileDiscrepancyRoundtripMapsAllFields() {
        BillReconcileDiscrepancy domain = new BillReconcileDiscrepancy();
        domain.setId(3L);
        domain.setImportId(1L);
        domain.setBillDate("2027-05-06");
        domain.setBizType("PAY");
        domain.setDiscrepancyType("PAY_CHANNEL_ONLY");
        domain.setBizNo("MCH456");
        domain.setChannelSerialNo("WX123");
        domain.setChannelAmount(100);
        domain.setLocalAmount(90);
        domain.setChannelStatus("SUCCESS");
        domain.setLocalStatus("NOT_FOUND");
        domain.setStatus("OPEN");
        domain.setResolveRemark("待核对");
        domain.setResolvedTime(new Date(6_000L));
        domain.setResolvedBy("admin");
        domain.setCreateTime(new Date(1_000L));
        domain.setUpdateTime(new Date(2_000L));

        BillReconcileDiscrepancy back =
            BillReconcileDiscrepancyPOConverter.toDomain(BillReconcileDiscrepancyPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getImportId(), back.getImportId());
        assertEquals(domain.getBillDate(), back.getBillDate());
        assertEquals(domain.getBizType(), back.getBizType());
        assertEquals(domain.getDiscrepancyType(), back.getDiscrepancyType());
        assertEquals(domain.getBizNo(), back.getBizNo());
        assertEquals(domain.getChannelSerialNo(), back.getChannelSerialNo());
        assertEquals(domain.getChannelAmount(), back.getChannelAmount());
        assertEquals(domain.getLocalAmount(), back.getLocalAmount());
        assertEquals(domain.getChannelStatus(), back.getChannelStatus());
        assertEquals(domain.getLocalStatus(), back.getLocalStatus());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getResolveRemark(), back.getResolveRemark());
        assertEquals(domain.getResolvedTime(), back.getResolvedTime());
        assertEquals(domain.getResolvedBy(), back.getResolvedBy());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void billReconcileDiscrepancyConverterNullSafe() {
        assertNull(BillReconcileDiscrepancyPOConverter.toPO(null));
        assertNull(BillReconcileDiscrepancyPOConverter.toDomain((BillReconcileDiscrepancyPO) null));
    }
}
