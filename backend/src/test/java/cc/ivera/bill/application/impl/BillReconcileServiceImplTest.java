package cc.ivera.bill.application.impl;

import cc.ivera.bill.domain.enums.BillDiscrepancyType;
import cc.ivera.bill.domain.enums.BillImportStatus;
import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.domain.model.BillReconcileDrilldown;
import cc.ivera.bill.domain.model.BillReconcileSummary;
import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.domain.repository.BillImportRepository;
import cc.ivera.bill.domain.repository.BillReconcileDiscrepancyRepository;
import cc.ivera.bill.domain.repository.BillRecordRepository;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 账账核对特征测试：渠道账(t_bill_record)必须与平台交易账(t_payment_order/t_refund_order)逐笔核对。
 */
class BillReconcileServiceImplTest {

    private BillImportRepository importRepository;
    private BillRecordRepository recordRepository;
    private BillReconcileDiscrepancyRepository discrepancyRepository;
    private PaymentOrderRepository paymentOrderRepository;
    private RefundOrderRepository refundOrderRepository;
    private BillReconcileServiceImpl service;

    @BeforeEach
    void setUp() {
        importRepository = mock(BillImportRepository.class);
        recordRepository = mock(BillRecordRepository.class);
        discrepancyRepository = mock(BillReconcileDiscrepancyRepository.class);
        paymentOrderRepository = mock(PaymentOrderRepository.class);
        refundOrderRepository = mock(RefundOrderRepository.class);
        DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        when(lockTemplate.execute(anyString(), anyLong(), anyLong(), any(Supplier.class))).thenAnswer(inv ->
            ((Supplier<?>) inv.getArgument(3)).get());
        when(paymentOrderRepository.listByChannelAndOrderNos(anyString(), anyCollection())).thenReturn(Collections.emptyList());
        when(paymentOrderRepository.listByChannelAndChannelOrderNos(anyString(), anyCollection())).thenReturn(Collections.emptyList());
        when(paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(anyString(), any(Date.class), any(Date.class)))
            .thenReturn(Collections.emptyList());
        when(paymentOrderRepository.listByPaymentNos(anyCollection())).thenReturn(Collections.emptyList());
        when(refundOrderRepository.listByRefundNos(anyCollection())).thenReturn(Collections.emptyList());
        when(refundOrderRepository.listSuccessBySuccessTimeRange(any(Date.class), any(Date.class)))
            .thenReturn(Collections.emptyList());
        doAnswer(inv -> {
            Consumer<?> consumer = inv.getArgument(0);
            @SuppressWarnings("unchecked")
            Consumer<org.springframework.transaction.TransactionStatus> typed =
                (Consumer<org.springframework.transaction.TransactionStatus>) consumer;
            typed.accept(new SimpleTransactionStatus());
            return null;
        }).when(tx).executeWithoutResult(any());

        service = new BillReconcileServiceImpl(importRepository, recordRepository, discrepancyRepository,
            paymentOrderRepository, refundOrderRepository, lockTemplate, tx);
    }

    @Test
    void uploadBill_rejectsNonXlsxFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "wx-trade-bill.csv", "text/csv", "not-xlsx".getBytes(StandardCharsets.UTF_8));

        BizException error = assertThrows(BizException.class,
            () -> service.uploadBill(file, "2026-09-09", "tradebill"));

        assertTrue(error.getMessage().contains("XLSX"));
        verifyNoInteractions(importRepository, recordRepository, discrepancyRepository);
    }

    @Test
    void reconcilePay_preservesOneOrderWithMultipleSuccessfulPaymentAttempts() {
        BillImport batch = batch("SUCCESS");
        when(importRepository.findByImportNo("BILL1")).thenReturn(batch);
        when(importRepository.findById(1L)).thenReturn(batch);

        BillRecord wx1 = payRecord("ORD1", "WX-TX-1", 100);
        BillRecord wx2 = payRecord("ORD1", "WX-TX-2", 100);
        when(recordRepository.listByImport(1L, null)).thenReturn(Arrays.asList(wx1, wx2));

        PaymentOrder p1 = payment("PM1", "ORD1", "WX-TX-1", 100);
        PaymentOrder p2 = payment("PM2", "ORD1", "WX-TX-2", 100);
        when(paymentOrderRepository.listByChannelAndOrderNos(eq("WXPAY"), anyCollection()))
            .thenReturn(Arrays.asList(p1, p2));
        when(paymentOrderRepository.listByChannelAndChannelOrderNos(eq("WXPAY"), anyCollection()))
            .thenReturn(Arrays.asList(p1, p2));
        when(paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(eq("WXPAY"), any(Date.class), any(Date.class)))
            .thenReturn(Arrays.asList(p1, p2));

        service.reconcileByImportNo("BILL1");

        assertEquals(2, batch.getMatchedCount());
        assertEquals(0, batch.getDiscrepancyCount());
        verify(discrepancyRepository, never()).save(any());
    }

    @Test
    void reconcilePay_sameOrderButDifferentChannelSerial_isExplicitMismatch() {
        BillImport batch = batch("SUCCESS");
        when(importRepository.findByImportNo("BILL1")).thenReturn(batch);
        when(importRepository.findById(1L)).thenReturn(batch);
        when(recordRepository.listByImport(1L, null)).thenReturn(Collections.singletonList(
            payRecord("ORD1", "WX-BILL", 100)));

        PaymentOrder local = payment("PM1", "ORD1", "WX-LOCAL", 100);
        when(paymentOrderRepository.listByChannelAndOrderNos(eq("WXPAY"), anyCollection()))
            .thenReturn(Collections.singletonList(local));
        when(paymentOrderRepository.listByChannelAndChannelOrderNos(eq("WXPAY"), anyCollection()))
            .thenReturn(Collections.emptyList());
        when(paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(eq("WXPAY"), any(Date.class), any(Date.class)))
            .thenReturn(Collections.singletonList(local));

        service.reconcileByImportNo("BILL1");

        ArgumentCaptor<BillReconcileDiscrepancy> captor = ArgumentCaptor.forClass(BillReconcileDiscrepancy.class);
        verify(discrepancyRepository).save(captor.capture());
        assertEquals(BillDiscrepancyType.PAY_SERIAL_MISMATCH.getType(), captor.getValue().getDiscrepancyType());
        assertEquals("WX-BILL", captor.getValue().getChannelSerialNo());
        assertEquals("ORD1", captor.getValue().getLocalBizNo());
        assertEquals("PM1", captor.getValue().getLocalLedgerNo());
        assertEquals("WX-LOCAL", captor.getValue().getLocalSerialNo());
        assertEquals(0, batch.getMatchedCount());
        assertEquals(1, batch.getDiscrepancyCount());
    }

    @Test
    void reconcileRefund_allBillRefundStatus_isTreatedAsSettledChannelLedger() {
        BillImport batch = batch("ALL");
        when(importRepository.findByImportNo("BILL1")).thenReturn(batch);
        when(importRepository.findById(1L)).thenReturn(batch);
        BillRecord channelRefund = refundRecord("RFD1", "WX-RFD-1", 200);
        channelRefund.setTradeStatus("REFUND");
        when(recordRepository.listByImport(1L, null)).thenReturn(Collections.singletonList(channelRefund));

        RefundOrder refund = new RefundOrder();
        refund.setRefundNo("RFD1");
        refund.setPaymentNo("PM1");
        refund.setRefundAmount(200);
        refund.setStatus("SUCCESS");
        refund.setSuccessTime(new Date());
        when(refundOrderRepository.listByRefundNos(anyCollection())).thenReturn(Collections.singletonList(refund));
        when(refundOrderRepository.listSuccessBySuccessTimeRange(any(Date.class), any(Date.class)))
            .thenReturn(Collections.singletonList(refund));
        PaymentOrder sourcePayment = payment("PM1", "ORD1", "WX-TX-1", 1000);
        when(paymentOrderRepository.listByPaymentNos(anyCollection())).thenReturn(Collections.singletonList(sourcePayment));

        service.reconcileByImportNo("BILL1");

        assertEquals(1, batch.getMatchedCount());
        assertEquals(0, batch.getDiscrepancyCount());
        verify(discrepancyRepository, never()).save(any());
    }

    @Test
    void summary_balancesChannelAndPlatformBooksBySuccessDate() {
        BillImport batch = batch("ALL");
        batch.setMatchedCount(2);
        when(importRepository.findByImportNo("BILL1")).thenReturn(batch);
        when(recordRepository.listByImport(1L, null)).thenReturn(Arrays.asList(
            payRecord("ORD1", "WX-TX-1", 1000), refundRecord("RFD1", "WX-RFD-1", 200)));
        when(discrepancyRepository.listByImport(1L, null, null, null)).thenReturn(Collections.emptyList());

        PaymentOrder payment = payment("PM1", "ORD1", "WX-TX-1", 1000);
        when(paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(eq("WXPAY"), any(Date.class), any(Date.class)))
            .thenReturn(Collections.singletonList(payment));
        when(paymentOrderRepository.listByPaymentNos(anyCollection())).thenReturn(Collections.singletonList(payment));

        RefundOrder refund = new RefundOrder();
        refund.setRefundNo("RFD1");
        refund.setPaymentNo("PM1");
        refund.setRefundAmount(200);
        refund.setStatus("SUCCESS");
        refund.setSuccessTime(new Date());
        when(refundOrderRepository.listSuccessBySuccessTimeRange(any(Date.class), any(Date.class)))
            .thenReturn(Collections.singletonList(refund));

        BillReconcileSummary summary = service.getSummary("BILL1");

        assertTrue(summary.getBalanced());
        assertEquals(1000L, summary.getChannelPayAmount());
        assertEquals(1000L, summary.getLocalPayAmount());
        assertEquals(200L, summary.getChannelRefundAmount());
        assertEquals(200L, summary.getLocalRefundAmount());
        assertEquals(800L, summary.getChannelNetAmount());
        assertEquals(800L, summary.getLocalNetAmount());
        assertEquals(0L, summary.getNetDifference());
    }

    @Test
    void summary_equalAmountsButOpenDiscrepancy_isNotBalanced() {
        BillImport batch = batch("SUCCESS");
        batch.setMatchedCount(1);
        batch.setDiscrepancyCount(1);
        when(importRepository.findByImportNo("BILL1")).thenReturn(batch);
        when(recordRepository.listByImport(1L, null))
            .thenReturn(Collections.singletonList(payRecord("ORD1", "WX-TX-1", 1000)));

        PaymentOrder payment = payment("PM1", "ORD1", "WX-TX-1", 1000);
        when(paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(eq("WXPAY"), any(Date.class), any(Date.class)))
            .thenReturn(Collections.singletonList(payment));

        BillReconcileDiscrepancy open = new BillReconcileDiscrepancy();
        open.setStatus("OPEN");
        when(discrepancyRepository.listByImport(1L, null, null, null))
            .thenReturn(Collections.singletonList(open));

        BillReconcileSummary summary = service.getSummary("BILL1");

        assertEquals(0L, summary.getNetDifference());
        assertEquals(1, summary.getOpenDiscrepancyCount());
        assertFalse(summary.getBalanced());
    }

    @Test
    void drilldown_returnsChannelRawLineAndPlatformLedgerVerification() {
        BillReconcileDiscrepancy discrepancy = new BillReconcileDiscrepancy();
        discrepancy.setId(9L);
        discrepancy.setImportId(1L);
        discrepancy.setBizType("PAY");
        discrepancy.setDiscrepancyType("PAY_SERIAL_MISMATCH");
        discrepancy.setBizNo("ORD1");
        discrepancy.setChannelSerialNo("WX-BILL");
        discrepancy.setLocalBizNo("ORD1");
        discrepancy.setLocalLedgerNo("PM1");
        discrepancy.setLocalSerialNo("WX-LOCAL");
        when(discrepancyRepository.findById(9L)).thenReturn(discrepancy);

        BillRecord channel = payRecord("ORD1", "WX-BILL", 1000);
        channel.setId(11L);
        channel.setRawLine("`2026-09-09 10:00:00,`WX-BILL,`ORD1,...");
        when(recordRepository.listByImport(1L, "PAY")).thenReturn(Collections.singletonList(channel));

        PaymentOrder local = payment("PM1", "ORD1", "WX-LOCAL", 1000);
        when(paymentOrderRepository.findByPaymentNo("PM1")).thenReturn(local);
        when(paymentOrderRepository.listByChannelAndChannelOrderNos(eq("WXPAY"), anyCollection()))
            .thenReturn(Collections.singletonList(local));
        when(paymentOrderRepository.listByChannelAndOrderNos(eq("WXPAY"), anyCollection()))
            .thenReturn(Collections.singletonList(local));

        BillReconcileDrilldown result = service.getDrilldown(9L);

        assertEquals(1, result.getChannelRecords().size());
        assertEquals(channel.getRawLine(), result.getChannelRecords().get(0).getRawLine());
        assertEquals(1, result.getLocalLedgers().size());
        assertEquals("PM1", result.getLocalLedgers().get(0).getLedgerNo());
        assertEquals(4, result.getVerificationItems().size());
        assertEquals(Boolean.FALSE, result.getVerificationItems().get(1).getMatched());
        assertEquals("渠道流水号", result.getVerificationItems().get(1).getField());
    }

    private BillImport batch(String kind) {
        BillImport batch = new BillImport();
        batch.setId(1L);
        batch.setImportNo("BILL1");
        batch.setBillDate("2026-09-09");
        batch.setBillType("TRADE");
        batch.setBillKind(kind);
        batch.setChannelCode("WXPAY");
        batch.setStatus(BillImportStatus.IMPORTED.getType());
        return batch;
    }

    private BillRecord payRecord(String orderNo, String serialNo, int amount) {
        BillRecord record = new BillRecord();
        record.setRecordType("PAY");
        record.setBizNo(orderNo);
        record.setChannelSerialNo(serialNo);
        record.setTradeStatus("SUCCESS");
        record.setTotalAmount(amount);
        return record;
    }

    private BillRecord refundRecord(String refundNo, String serialNo, int amount) {
        BillRecord record = new BillRecord();
        record.setRecordType("REFUND");
        record.setBizNo(refundNo);
        record.setChannelSerialNo(serialNo);
        record.setTradeStatus("SUCCESS");
        record.setRefundAmount(amount);
        return record;
    }

    private PaymentOrder payment(String paymentNo, String orderNo, String serialNo, int amount) {
        PaymentOrder payment = new PaymentOrder();
        payment.setId((long) paymentNo.hashCode());
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(orderNo);
        payment.setChannel("WXPAY");
        payment.setChannelOrderNo(serialNo);
        payment.setStatus("SUCCESS");
        payment.setPaidAmount(amount);
        payment.setPaidTime(new Date());
        return payment;
    }
}
