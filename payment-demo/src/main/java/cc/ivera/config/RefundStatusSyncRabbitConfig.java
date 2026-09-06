package cc.ivera.config;

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
    public static final String REFUND_STATUS_SYNC_DELAY_ROUTING_KEY = "payment.refund.status-sync.delay";
    public static final String REFUND_STATUS_SYNC_RELEASE_ROUTING_KEY = "payment.refund.status-sync.release";

    @Bean(name = "refundStatusSyncEventExchange")
    public DirectExchange refundStatusSyncEventExchange() {
        return new DirectExchange(REFUND_STATUS_SYNC_EVENT_EXCHANGE, true, false);
    }

    @Bean(name = "refundStatusSyncDeadLetterExchange")
    public DirectExchange refundStatusSyncDeadLetterExchange() {
        return new DirectExchange(REFUND_STATUS_SYNC_DEAD_LETTER_EXCHANGE, true, false);
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
        return new Queue(REFUND_STATUS_SYNC_RELEASE_QUEUE, true);
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
}

