package cc.ivera.order.infrastructure.mq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCloseRabbitConfigTest {

    private final OrderCloseRabbitConfig config = new OrderCloseRabbitConfig();

    @Test
    void releaseQueue_deadLettersRejectedMessagesToFailureQueue() {
        Queue releaseQueue = config.orderCloseReleaseQueue();

        assertTrue(releaseQueue.isDurable());
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_FAILURE_EXCHANGE,
            releaseQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_FAILURE_ROUTING_KEY,
            releaseQueue.getArguments().get("x-dead-letter-routing-key"));

        DirectExchange failureExchange = config.orderCloseFailureExchange();
        Queue failureQueue = config.orderCloseFailureQueue();
        Binding failureBinding = config.orderCloseFailureBinding(failureQueue, failureExchange);

        assertTrue(failureQueue.isDurable());
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_FAILURE_QUEUE, failureQueue.getName());
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_FAILURE_EXCHANGE, failureBinding.getExchange());
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_FAILURE_ROUTING_KEY, failureBinding.getRoutingKey());
    }

    @Test
    void parkingLotQueue_isDurableAndHasDedicatedRoutingKey() {
        DirectExchange failureExchange = config.orderCloseFailureExchange();
        Queue parkingLotQueue = config.orderCloseParkingLotQueue();
        Binding parkingLotBinding = config.orderCloseParkingLotBinding(parkingLotQueue, failureExchange);

        assertTrue(parkingLotQueue.isDurable());
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_PARKING_LOT_QUEUE, parkingLotQueue.getName());
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_FAILURE_EXCHANGE, parkingLotBinding.getExchange());
        assertEquals(OrderCloseRabbitConfig.ORDER_CLOSE_PARKING_LOT_ROUTING_KEY, parkingLotBinding.getRoutingKey());
    }
}
