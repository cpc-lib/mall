package cc.ivera.refund.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundStatusSyncRabbitConfigTest {

    private final RefundStatusSyncRabbitConfig config = new RefundStatusSyncRabbitConfig();

    @Test
    void releaseQueue_deadLettersRejectedMessagesToFailureQueue() {
        Queue releaseQueue = config.refundStatusSyncReleaseQueue();

        assertTrue(releaseQueue.isDurable());
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_FAILURE_EXCHANGE,
            releaseQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_FAILURE_ROUTING_KEY,
            releaseQueue.getArguments().get("x-dead-letter-routing-key"));

        DirectExchange failureExchange = config.refundStatusSyncFailureExchange();
        Queue failureQueue = config.refundStatusSyncFailureQueue();
        Binding failureBinding = config.refundStatusSyncFailureBinding(failureQueue, failureExchange);

        assertTrue(failureQueue.isDurable());
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_FAILURE_QUEUE, failureQueue.getName());
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_FAILURE_EXCHANGE, failureBinding.getExchange());
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_FAILURE_ROUTING_KEY, failureBinding.getRoutingKey());
    }

    @Test
    void parkingLotQueue_isDurableAndHasDedicatedRoutingKey() {
        DirectExchange failureExchange = config.refundStatusSyncFailureExchange();
        Queue parkingLotQueue = config.refundStatusSyncParkingLotQueue();
        Binding parkingLotBinding = config.refundStatusSyncParkingLotBinding(parkingLotQueue, failureExchange);

        assertTrue(parkingLotQueue.isDurable());
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_PARKING_LOT_QUEUE, parkingLotQueue.getName());
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_FAILURE_EXCHANGE, parkingLotBinding.getExchange());
        assertEquals(RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_PARKING_LOT_ROUTING_KEY, parkingLotBinding.getRoutingKey());
    }
}
