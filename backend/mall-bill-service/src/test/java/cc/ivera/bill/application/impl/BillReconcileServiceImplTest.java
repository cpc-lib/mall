package cc.ivera.bill.application.impl;

import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.api.refund.RefundLedgerQueryApi;
import cc.ivera.bill.domain.enums.BillDiscrepancyStatus;
import cc.ivera.bill.domain.enums.BillDiscrepancyType;
import cc.ivera.bill.domain.enums.BillImportStatus;
import cc.ivera.bill.domain.enums.BillKind;
import cc.ivera.bill.domain.enums.BillRecordType;
import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.domain.model.BillRecordSummary;
import cc.ivera.bill.domain.repository.BillImportRepository;
import cc.ivera.bill.domain.repository.BillReconcileDiscrepancyRepository;
import cc.ivera.bill.domain.repository.BillRecordRepository;
import cc.ivera.payment.domain.enums.PaymentOrderStatus;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillReconcileServiceImplTest {

    private static final String DATE = "2026-11-25";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final BillImportRepository importRepo = mock(BillImportRepository.class);
    private final BillRecordRepository recordRepo = mock(BillRecordRepository.class);
    private final BillReconcileDiscrepancyRepository discrepancyRepo = mock(BillReconcileDiscrepancyRepository.class);
    private final PaymentOrderQueryApi paymentRepo = mock(PaymentOrderQueryApi.class);
    private final RefundLedgerQueryApi refundRepo = mock(RefundLedgerQueryApi.class);
    private final DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    private BillReconcileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BillReconcileServiceImpl(
            importRepo, recordRepo, discrepancyRepo, paymentRepo, refundRepo, lockTemplate, tx);
        when(lockTemplate.execute(anyString(), anyLong(), ArgumentMatchers.<Supplier<Object>>any()))
            .thenAnswer(invocation -> invocation.getArgument(2, Supplier.class).get());
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    @Test
    void oneOrderWithTwoChannelSerialsMatchesBothPaymentAttempts() {
        BillImport batch = batch(BillKind.ALL.getType());
        List<BillRecord> records = List.of(
            payRecord("ORD-1", "WX-1", 100),
            payRecord("ORD-1", "WX-2", 200));
        PaymentOrder first = payment("PAY-1", "ORD-1", "WX-1", 100, PaymentOrderStatus.SUCCESS.getType());
        PaymentOrder second = payment("PAY-2", "ORD-1", "WX-2", 200, PaymentOrderStatus.SUCCESS.getType());
        stubReconcile(batch, records);
        when(paymentRepo.listByChannelAndOrderNos(eq("WXPAY"), anyCollection())).thenReturn(List.of(first, second));
        when(paymentRepo.listByChannelAndChannelOrderNos(eq("WXPAY"), anyCollection())).thenReturn(List.of(first, second));
        when(paymentRepo.listSuccessByChannelAndPaidTimeRange(anyString(), any(), any()))
            .thenReturn(List.of(first, second));

        service.reconcileByImportNo(batch.getImportNo());

        assertEquals(2, batch.getMatchedCount());
        assertEquals(0, batch.getDiscrepancyCount());
        assertTrue(savedDiscrepancies().isEmpty());
    }

    @Test
    void scansBothChannelOnlyAndLocalOnlyPayments() {
        BillImport batch = batch(BillKind.SUCCESS.getType());
        stubReconcile(batch, List.of(payRecord("ORD-CHANNEL", "WX-CHANNEL", 100)));
        PaymentOrder localOnly = payment("PAY-LOCAL", "ORD-LOCAL", "WX-LOCAL", 200, PaymentOrderStatus.SUCCESS.getType());
        when(paymentRepo.listSuccessByChannelAndPaidTimeRange(anyString(), any(), any()))
            .thenReturn(List.of(localOnly));

        service.reconcileByImportNo(batch.getImportNo());

        assertEquals(Set.of(
            BillDiscrepancyType.PAY_CHANNEL_ONLY.getType(),
            BillDiscrepancyType.PAY_LOCAL_ONLY.getType()), discrepancyTypes());
        assertEquals(0, batch.getMatchedCount());
    }

    @Test
    void reportsAmountAndStatusMismatchForSameChannelSerial() {
        BillImport batch = batch(BillKind.SUCCESS.getType());
        stubReconcile(batch, List.of(payRecord("ORD-1", "WX-1", 100)));
        PaymentOrder local = payment("PAY-1", "ORD-1", "WX-1", 90, PaymentOrderStatus.PAYING.getType());
        when(paymentRepo.listByChannelAndChannelOrderNos(eq("WXPAY"), anyCollection())).thenReturn(List.of(local));

        service.reconcileByImportNo(batch.getImportNo());

        assertEquals(Set.of(
            BillDiscrepancyType.PAY_AMOUNT_MISMATCH.getType(),
            BillDiscrepancyType.PAY_STATUS_MISMATCH.getType()), discrepancyTypes());
        assertEquals(0, batch.getMatchedCount());
    }

    @Test
    void queriesLocalLedgersAtShanghaiHalfOpenDayBoundary() {
        BillImport batch = batch(BillKind.ALL.getType());
        stubReconcile(batch, List.of());

        service.reconcileByImportNo(batch.getImportNo());

        ArgumentCaptor<Date> payStart = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> payEnd = ArgumentCaptor.forClass(Date.class);
        verify(paymentRepo).listSuccessByChannelAndPaidTimeRange(eq("WXPAY"), payStart.capture(), payEnd.capture());
        ArgumentCaptor<Date> refundStart = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> refundEnd = ArgumentCaptor.forClass(Date.class);
        verify(refundRepo).listSuccessBySuccessTimeRange(refundStart.capture(), refundEnd.capture());

        Date expectedStart = Date.from(LocalDate.parse(DATE).atStartOfDay(ZONE).toInstant());
        Date expectedEnd = Date.from(LocalDate.parse(DATE).plusDays(1).atStartOfDay(ZONE).toInstant());
        assertEquals(expectedStart, payStart.getValue());
        assertEquals(expectedEnd, payEnd.getValue());
        assertEquals(expectedStart, refundStart.getValue());
        assertEquals(expectedEnd, refundEnd.getValue());
    }

    @Test
    void summaryIsNotBalancedWhenOffsettingDiscrepanciesLeaveNetDifferenceZero() {
        BillImport batch = batch(BillKind.ALL.getType());
        when(importRepo.findByImportNo(batch.getImportNo())).thenReturn(batch);
        BillRecordSummary channel = new BillRecordSummary();
        channel.setPayCount(2);
        channel.setPayAmount(300);
        channel.setRefundCount(1);
        channel.setRefundAmount(100);
        when(recordRepo.summarizeByImport(1L)).thenReturn(channel);
        PaymentOrder first = payment("PAY-1", "ORD-1", "WX-1", 100, PaymentOrderStatus.SUCCESS.getType());
        PaymentOrder second = payment("PAY-2", "ORD-2", "WX-2", 200, PaymentOrderStatus.SUCCESS.getType());
        when(paymentRepo.listSuccessByChannelAndPaidTimeRange(anyString(), any(), any()))
            .thenReturn(List.of(first, second));
        RefundOrder refund = new RefundOrder();
        refund.setRefundNo("REF-1");
        refund.setPaymentNo("PAY-1");
        refund.setRefundAmount(100);
        refund.setStatus(RefundOrderStatus.SUCCESS.getType());
        when(refundRepo.listSuccessBySuccessTimeRange(any(), any())).thenReturn(List.of(refund));
        when(paymentRepo.listByPaymentNos(anyCollection())).thenReturn(List.of(first));
        when(discrepancyRepo.countByImport(eq(1L), isNull(), isNull(), isNull())).thenReturn(2L);
        when(discrepancyRepo.countByImport(eq(1L), isNull(), isNull(),
            eq(BillDiscrepancyStatus.OPEN.getType()))).thenReturn(2L);

        var summary = service.getSummary(batch.getImportNo());

        assertEquals(0, summary.getNetDifference());
        assertEquals(2, summary.getOpenDiscrepancyCount());
        assertFalse(summary.getBalanced());
    }

    private void stubReconcile(BillImport batch, List<BillRecord> records) {
        when(importRepo.findByImportNo(batch.getImportNo())).thenReturn(batch);
        when(importRepo.findById(batch.getId())).thenReturn(batch);
        when(recordRepo.listForReconcile(batch.getId())).thenReturn(records);
    }

    private List<BillReconcileDiscrepancy> savedDiscrepancies() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BillReconcileDiscrepancy>> captor = ArgumentCaptor.forClass(List.class);
        verify(discrepancyRepo).saveBatch(captor.capture());
        return captor.getValue();
    }

    private Set<String> discrepancyTypes() {
        return savedDiscrepancies().stream()
            .map(BillReconcileDiscrepancy::getDiscrepancyType)
            .collect(Collectors.toSet());
    }

    private static BillImport batch(String kind) {
        BillImport batch = new BillImport();
        batch.setId(1L);
        batch.setImportNo("IMP-1");
        batch.setBillDate(DATE);
        batch.setBillKind(kind);
        batch.setStatus(BillImportStatus.IMPORTED.getType());
        return batch;
    }

    private static BillRecord payRecord(String orderNo, String serialNo, int amount) {
        BillRecord record = new BillRecord();
        record.setRecordType(BillRecordType.PAY.getType());
        record.setBizNo(orderNo);
        record.setChannelSerialNo(serialNo);
        record.setTotalAmount(amount);
        record.setTradeStatus("SUCCESS");
        return record;
    }

    private static PaymentOrder payment(String paymentNo, String orderNo, String serialNo,
                                        int amount, String status) {
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(orderNo);
        payment.setChannel("WXPAY");
        payment.setChannelOrderNo(serialNo);
        payment.setPaidAmount(amount);
        payment.setStatus(status);
        return payment;
    }
}
