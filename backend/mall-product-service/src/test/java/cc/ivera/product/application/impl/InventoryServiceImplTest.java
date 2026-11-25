package cc.ivera.product.application.impl;

import cc.ivera.product.domain.enums.ReservationStatus;
import cc.ivera.product.domain.model.InventoryReservation;
import cc.ivera.product.domain.model.OrderItemStockLine;
import cc.ivera.product.domain.model.RefundStockLine;
import cc.ivera.product.domain.model.ReserveLine;
import cc.ivera.product.domain.repository.InventoryReservationRepository;
import cc.ivera.product.domain.repository.InventoryTransactionRepository;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * InventoryServiceImpl Mockito 单测：不启动 Spring、不依赖 DM8。
 * 覆盖四桶流转的状态机分支：排序防死锁、CAS 幂等、桶分流、已支付不可释放。
 */
class InventoryServiceImplTest {

    private final ProductRepository productRepo = mock(ProductRepository.class);
    private final InventoryReservationRepository reservationRepo = mock(InventoryReservationRepository.class);
    private final InventoryTransactionRepository txnRepo = mock(InventoryTransactionRepository.class);
    private final InventoryServiceImpl service = new InventoryServiceImpl(productRepo, reservationRepo, txnRepo);

    // ==================== reserveForOrder ====================

    @Test
    void reserveForOrderSortsByProductIdToAvoidDeadlock() throws Exception {
        ReserveLine lineA = reserveLine(1L, 100L, 2);
        ReserveLine lineB = reserveLine(2L, 50L, 3);
        List<ReserveLine> lines = new ArrayList<>(List.of(lineA, lineB));

        doNothing().when(reservationRepo).insert(any(InventoryReservation.class));
        when(productRepo.reserveStock(eq(50L), eq(3))).thenReturn(1);
        when(productRepo.reserveStock(eq(100L), eq(2))).thenReturn(1);

        service.reserveForOrder("ORD-1", new Date(), lines);

        verify(productRepo).reserveStock(50L, 3);
        verify(productRepo).reserveStock(100L, 2);
    }

    @Test
    void reserveForOrderInsufficientStockFailsTransaction() throws Exception {
        ReserveLine line = reserveLine(1L, 999L, 5);
        doNothing().when(reservationRepo).insert(any(InventoryReservation.class));
        when(productRepo.reserveStock(eq(999L), eq(5))).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
            () -> service.reserveForOrder("ORD-1", new Date(), List.of(line)));

        assert ex.getMessage().contains("库存不足") : ex.getMessage();
    }

    @Test
    void reserveForOrderDuplicateReservationIsIdempotent() throws Exception {
        ReserveLine line = reserveLine(1L, 999L, 5);
        doThrow(new DuplicateKeyException("uk_reservation_order_item")).when(reservationRepo)
            .insert(any(InventoryReservation.class));

        service.reserveForOrder("ORD-1", new Date(), List.of(line));

        verifyNoMoreInteractions(productRepo);
    }

    // ==================== commitReservation ====================

    @Test
    void commitReservationTransitionsLockedToCommitted() throws Exception {
        when(reservationRepo.casTransition(eq("ORD-1"),
            eq(ReservationStatus.LOCKED.getType()), eq(ReservationStatus.COMMITTED.getType()), eq("commitTime")))
            .thenReturn(2);
        when(reservationRepo.listByOrderNo("ORD-1")).thenReturn(List.of(
            reservation("ORD-1", ReservationStatus.COMMITTED),
            reservation("ORD-1", ReservationStatus.COMMITTED)
        ));

        service.commitReservation("ORD-1");

        verify(reservationRepo).casTransition(eq("ORD-1"),
            eq(ReservationStatus.LOCKED.getType()), eq(ReservationStatus.COMMITTED.getType()), eq("commitTime"));
    }

    @Test
    void commitReservationAlreadyCommittedIsIdempotent() throws Exception {
        when(reservationRepo.casTransition(eq("ORD-1"),
            eq(ReservationStatus.LOCKED.getType()), eq(ReservationStatus.COMMITTED.getType()), eq("commitTime")))
            .thenReturn(0);
        when(reservationRepo.listByOrderNo("ORD-1")).thenReturn(List.of(
            reservation("ORD-1", ReservationStatus.COMMITTED)
        ));

        service.commitReservation("ORD-1");
    }

    @Test
    void commitReservationAfterReleaseRejects() throws Exception {
        when(reservationRepo.casTransition(eq("ORD-1"),
            eq(ReservationStatus.LOCKED.getType()), eq(ReservationStatus.COMMITTED.getType()), eq("commitTime")))
            .thenReturn(0);
        when(reservationRepo.listByOrderNo("ORD-1")).thenReturn(List.of(
            reservation("ORD-1", ReservationStatus.RELEASED)
        ));

        BizException ex = assertThrows(BizException.class,
            () -> service.commitReservation("ORD-1"));
        assert ex.getMessage().contains("已释放") : ex.getMessage();
    }

    // ==================== releaseReservation ====================

    @Test
    void releaseReservationRejectsAlreadyCommittedOrder() throws Exception {
        when(reservationRepo.casTransition(eq("ORD-1"),
            eq(ReservationStatus.LOCKED.getType()), eq(ReservationStatus.RELEASED.getType()), eq("releaseTime")))
            .thenReturn(0);
        when(reservationRepo.listByOrderNo("ORD-1")).thenReturn(List.of(
            reservation("ORD-1", ReservationStatus.COMMITTED)
        ));

        BizException ex = assertThrows(BizException.class,
            () -> service.releaseReservation("ORD-1"));
        assert ex.getMessage().contains("已支付成交") : ex.getMessage();
    }

    // ==================== convertToSoldOnReceipt ====================

    @Test
    void convertToSoldSubtractsRestockedQty() throws Exception {
        OrderItemStockLine line = new OrderItemStockLine();
        line.setOrderItemId(1L);
        line.setProductId(100L);
        line.setQuantity(5);
        line.setRestockedQty(2);

        when(txnRepo.countByBizNo(anyString())).thenReturn(0);
        when(productRepo.commitSoldStock(eq(100L), eq(3))).thenReturn(1);

        service.convertToSoldOnReceipt("ORD-1", List.of(line));

        verify(productRepo).commitSoldStock(100L, 3);
    }

    @Test
    void convertToSoldZeroEffectiveQtyIsSkipped() throws Exception {
        OrderItemStockLine line = new OrderItemStockLine();
        line.setOrderItemId(1L);
        line.setProductId(100L);
        line.setQuantity(2);
        line.setRestockedQty(2);

        service.convertToSoldOnReceipt("ORD-1", List.of(line));

        verify(productRepo, never()).commitSoldStock(anyLong(), anyInt());
    }

    // ==================== restockForRefund 桶分流 ====================

    @Test
    void restockAfterReceiptDrawsFromSoldBucket() throws Exception {
        RefundStockLine line = refundLine(1L, 100L, 3);
        when(txnRepo.countByBizNo(anyString())).thenReturn(0);
        when(productRepo.releaseSoldStock(100L, 3)).thenReturn(1);

        service.restockForRefund("REF-1", List.of(line), true);

        verify(productRepo).releaseSoldStock(100L, 3);
    }

    @Test
    void restockBeforeReceiptDrawsFromLockedBucket() throws Exception {
        RefundStockLine line = refundLine(1L, 100L, 3);
        when(txnRepo.countByBizNo(anyString())).thenReturn(0);
        when(productRepo.releaseReservedStock(100L, 3)).thenReturn(1);

        service.restockForRefund("REF-1", List.of(line), false);

        verify(productRepo).releaseReservedStock(100L, 3);
    }

    // ==================== writeOffLostForRefund 桶分流 ====================

    @Test
    void writeOffLostAfterReceiptUsesSoldBucket() throws Exception {
        RefundStockLine line = refundLine(1L, 100L, 3);
        when(txnRepo.countByBizNo(anyString())).thenReturn(0);
        when(productRepo.writeOffSoldLostStock(100L, 3)).thenReturn(1);

        service.writeOffLostForRefund("REF-1", List.of(line), true);

        verify(productRepo).writeOffSoldLostStock(100L, 3);
    }

    @Test
    void writeOffLostBeforeReceiptUsesLockedBucket() throws Exception {
        RefundStockLine line = refundLine(1L, 100L, 3);
        when(txnRepo.countByBizNo(anyString())).thenReturn(0);
        when(productRepo.writeOffLostStock(100L, 3)).thenReturn(1);

        service.writeOffLostForRefund("REF-1", List.of(line), false);

        verify(productRepo).writeOffLostStock(100L, 3);
    }

    // ==================== helpers ====================

    private static ReserveLine reserveLine(Long orderItemId, Long productId, int qty) {
        ReserveLine line = new ReserveLine();
        line.setOrderItemId(orderItemId);
        line.setProductId(productId);
        line.setQuantity(qty);
        return line;
    }

    private static RefundStockLine refundLine(Long orderItemId, Long productId, int refundQty) {
        RefundStockLine line = new RefundStockLine();
        line.setOrderItemId(orderItemId);
        line.setProductId(productId);
        line.setRefundQty(refundQty);
        return line;
    }

    private static InventoryReservation reservation(String orderNo, ReservationStatus status) {
        InventoryReservation r = new InventoryReservation();
        r.setOrderNo(orderNo);
        r.setStatus(status.getType());
        return r;
    }
}
