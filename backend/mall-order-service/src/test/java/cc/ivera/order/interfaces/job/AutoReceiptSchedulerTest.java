package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.ShipmentService;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.domain.repository.OrderShipmentRepository;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoReceiptSchedulerTest {

    @Test
    void confirmsEveryOverdueShipmentThroughExistingReceiptChain() {
        OrderShipmentRepository repository = mock(OrderShipmentRepository.class);
        ShipmentService shipmentService = mock(ShipmentService.class);
        DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);
        when(lockTemplate.tryExecute(any(String.class), any(Runnable.class)))
            .thenAnswer(invocation -> { invocation.getArgument(1, Runnable.class).run(); return true; });
        when(repository.listAutoReceiptCandidates(any(Date.class), eq(200)))
            .thenReturn(List.of(shipment("ORD-1"), shipment("ORD-2")));

        new AutoReceiptScheduler(repository, shipmentService, lockTemplate, 60_000L).scan();

        verify(shipmentService).confirmReceipt("ORD-1");
        verify(shipmentService).confirmReceipt("ORD-2");
    }

    @Test
    void singleFailureDoesNotBlockRemainingShipments() {
        OrderShipmentRepository repository = mock(OrderShipmentRepository.class);
        ShipmentService shipmentService = mock(ShipmentService.class);
        DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);
        when(lockTemplate.tryExecute(any(String.class), any(Runnable.class)))
            .thenAnswer(invocation -> { invocation.getArgument(1, Runnable.class).run(); return true; });
        when(repository.listAutoReceiptCandidates(any(Date.class), eq(200)))
            .thenReturn(List.of(shipment("ORD-1"), shipment("ORD-2")));
        doThrow(new IllegalStateException("concurrent status change"))
            .when(shipmentService).confirmReceipt("ORD-1");

        new AutoReceiptScheduler(repository, shipmentService, lockTemplate, 60_000L).scan();

        verify(shipmentService).confirmReceipt("ORD-1");
        verify(shipmentService).confirmReceipt("ORD-2");
    }

    private static OrderShipment shipment(String orderNo) {
        OrderShipment shipment = new OrderShipment();
        shipment.setOrderNo(orderNo);
        shipment.setTrackingNo("TRK-" + orderNo);
        return shipment;
    }
}
