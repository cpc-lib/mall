package cc.ivera.refund.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RefundStatusSyncRabbitConfig {

    public static final String REFUND_STATUS_SYNC_EVENT_EXCHANGE = "payment.refund.status-sync.event.exchange";
    public static final String REFUND_STATUS_SYNC_DEAD_LETTER_EXCHANGE = "payment.refund.status-sync.dead-letter.exchange";
    public static final String REFUND_STATUS_SYNC_DELAY_QUEUE = "payment.refund.status-sync.delay.queue";
    public static final String REFUND_STATUS_SYNC_RELEASE_QUEUE = "payment.refund.status-sync.release.queue";
    public static final String REFUND_STATUS_SYNC_FAILURE_EXCHANGE = "payment.refund.status-sync.failure.exchange";
    public static final String REFUND_STATUS_SYNC_FAILURE_QUEUE = "payment.refund.status-sync.failure.queue";
    public static final String REFUND_STATUS_SYNC_PARKING_LOT_QUEUE = "payment.refund.status-sync.parking-lot.queue";
    public static final String REFUND_STATUS_SYNC_DELAY_ROUTING_KEY = "payment.refund.status-sync.delay";
    public static final String REFUND_STATUS_SYNC_RELEASE_ROUTING_KEY = "payment.refund.status-sync.release";
    public static final String REFUND_STATUS_SYNC_FAILURE_ROUTING_KEY = "payment.refund.status-sync.failure";
    public static final String REFUND_STATUS_SYNC_PARKING_LOT_ROUTING_KEY = "payment.refund.status-sync.parking-lot";

    @Bean(name = "refundStatusSyncEventExchange")
    public DirectExchange refundStatusSyncEventExchange() {
        return new DirectExchange(REFUND_STATUS_SYNC_EVENT_EXCHANGE, true, false);
    }

    @Bean(name = "refundStatusSyncDeadLetterExchange")
    public DirectExchange refundStatusSyncDeadLetterExchange() {
        return new DirectExchange(REFUND_STATUS_SYNC_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean(name = "refundStatusSyncFailureExchange")
    public DirectExchange refundStatusSyncFailureExchange() {
        return new DirectExchange(REFUND_STATUS_SYNC_FAILURE_EXCHANGE, true, false);
    }

    @Bean(name = "refundStatusSyncDelayQueue")
    public Queue refundStatusSyncDelayQueue(@Value("${payment.refund.status-sync-delay-ms:60000}") long statusSyncDelayMs) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", REFUND_STATUS_SYNC_DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", REFUND_STATUS_SYNC_RELEASE_ROUTING_KEY);
        args.put("x-message-ttl", statusSyncDelayMs);
        return new Queue(REFUND_STATUS_SYNC_DELAY_QUEUE, true, false, false, args);
    }

    @Bean(name = "refundStatusSyncReleaseQueue")
    public Queue refundStatusSyncReleaseQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", REFUND_STATUS_SYNC_FAILURE_EXCHANGE);
        args.put("x-dead-letter-routing-key", REFUND_STATUS_SYNC_FAILURE_ROUTING_KEY);
        return new Queue(REFUND_STATUS_SYNC_RELEASE_QUEUE, true, false, false, args);
    }

    @Bean(name = "refundStatusSyncFailureQueue")
    public Queue refundStatusSyncFailureQueue() {
        return new Queue(REFUND_STATUS_SYNC_FAILURE_QUEUE, true);
    }

    @Bean(name = "refundStatusSyncParkingLotQueue")
    public Queue refundStatusSyncParkingLotQueue() {
        return new Queue(REFUND_STATUS_SYNC_PARKING_LOT_QUEUE, true);
    }

    @Bean
    public Binding refundStatusSyncDelayBinding(
        @Qualifier("refundStatusSyncDelayQueue") Queue refundStatusSyncDelayQueue,
        @Qualifier("refundStatusSyncEventExchange") DirectExchange refundStatusSyncEventExchange) {
        return BindingBuilder.bind(refundStatusSyncDelayQueue)
            .to(refundStatusSyncEventExchange)
            .with(REFUND_STATUS_SYNC_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding refundStatusSyncReleaseBinding(
        @Qualifier("refundStatusSyncReleaseQueue") Queue refundStatusSyncReleaseQueue,
        @Qualifier("refundStatusSyncDeadLetterExchange") DirectExchange refundStatusSyncDeadLetterExchange) {
        return BindingBuilder.bind(refundStatusSyncReleaseQueue)
            .to(refundStatusSyncDeadLetterExchange)
            .with(REFUND_STATUS_SYNC_RELEASE_ROUTING_KEY);
    }

    @Bean
    public Binding refundStatusSyncFailureBinding(
        @Qualifier("refundStatusSyncFailureQueue") Queue refundStatusSyncFailureQueue,
        @Qualifier("refundStatusSyncFailureExchange") DirectExchange refundStatusSyncFailureExchange) {
        return BindingBuilder.bind(refundStatusSyncFailureQueue)
            .to(refundStatusSyncFailureExchange)
            .with(REFUND_STATUS_SYNC_FAILURE_ROUTING_KEY);
    }

    @Bean
    public Binding refundStatusSyncParkingLotBinding(
        @Qualifier("refundStatusSyncParkingLotQueue") Queue refundStatusSyncParkingLotQueue,
        @Qualifier("refundStatusSyncFailureExchange") DirectExchange refundStatusSyncFailureExchange) {
        return BindingBuilder.bind(refundStatusSyncParkingLotQueue)
            .to(refundStatusSyncFailureExchange)
            .with(REFUND_STATUS_SYNC_PARKING_LOT_ROUTING_KEY);
    }
}
