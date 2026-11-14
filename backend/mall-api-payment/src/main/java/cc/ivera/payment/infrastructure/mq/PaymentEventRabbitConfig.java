package cc.ivera.payment.infrastructure.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付事件拓扑（P4a）：支付成交 / 重复支付 / 晚到支付。
 * 队列统一 MANUAL ACK + Spring Retry，重试耗尽后进入 Failure 队列，Parking Lot 人工隔离。
 */
@Configuration
public class PaymentEventRabbitConfig {

    public static final String PAYMENT_EVENT_EXCHANGE = "payment.event.exchange";
    public static final String PAYMENT_EVENT_FAILURE_EXCHANGE = "payment.event.failure.exchange";

    public static final String PAYMENT_SUCCEEDED_QUEUE = "payment.succeeded.order.queue";
    public static final String DUPLICATE_PAYMENT_QUEUE = "payment.duplicate.refund.queue";
    public static final String LATE_PAYMENT_QUEUE = "payment.late.refund.queue";

    public static final String PAYMENT_SUCCEEDED_FAILURE_QUEUE = "payment.succeeded.failure.queue";
    public static final String DUPLICATE_PAYMENT_FAILURE_QUEUE = "payment.duplicate.failure.queue";
    public static final String LATE_PAYMENT_FAILURE_QUEUE = "payment.late.failure.queue";
    public static final String PAYMENT_EVENT_PARKING_LOT_QUEUE = "payment.event.parking.queue";

    public static final String PAYMENT_SUCCEEDED_ROUTING_KEY = "payment.succeeded";
    public static final String DUPLICATE_PAYMENT_ROUTING_KEY = "payment.duplicate";
    public static final String LATE_PAYMENT_ROUTING_KEY = "payment.late";
    public static final String PAYMENT_SUCCEEDED_FAILURE_ROUTING_KEY = "payment.succeeded.failure";
    public static final String DUPLICATE_PAYMENT_FAILURE_ROUTING_KEY = "payment.duplicate.failure";
    public static final String LATE_PAYMENT_FAILURE_ROUTING_KEY = "payment.late.failure";
    public static final String PAYMENT_EVENT_PARKING_LOT_ROUTING_KEY = "payment.parking";

    @Bean(name = "paymentEventExchange")
    public DirectExchange paymentEventExchange() {
        return new DirectExchange(PAYMENT_EVENT_EXCHANGE, true, false);
    }

    @Bean(name = "paymentEventFailureExchange")
    public DirectExchange paymentEventFailureExchange() {
        return new DirectExchange(PAYMENT_EVENT_FAILURE_EXCHANGE, true, false);
    }

    @Bean(name = "paymentSucceededQueue")
    public Queue paymentSucceededQueue() {
        return eventQueue(PAYMENT_SUCCEEDED_QUEUE, PAYMENT_SUCCEEDED_FAILURE_ROUTING_KEY);
    }

    @Bean(name = "duplicatePaymentQueue")
    public Queue duplicatePaymentQueue() {
        return eventQueue(DUPLICATE_PAYMENT_QUEUE, DUPLICATE_PAYMENT_FAILURE_ROUTING_KEY);
    }

    @Bean(name = "latePaymentQueue")
    public Queue latePaymentQueue() {
        return eventQueue(LATE_PAYMENT_QUEUE, LATE_PAYMENT_FAILURE_ROUTING_KEY);
    }

    @Bean(name = "paymentSucceededFailureQueue")
    public Queue paymentSucceededFailureQueue() {
        return new Queue(PAYMENT_SUCCEEDED_FAILURE_QUEUE, true);
    }

    @Bean(name = "duplicatePaymentFailureQueue")
    public Queue duplicatePaymentFailureQueue() {
        return new Queue(DUPLICATE_PAYMENT_FAILURE_QUEUE, true);
    }

    @Bean(name = "latePaymentFailureQueue")
    public Queue latePaymentFailureQueue() {
        return new Queue(LATE_PAYMENT_FAILURE_QUEUE, true);
    }

    @Bean(name = "paymentEventParkingLotQueue")
    public Queue paymentEventParkingLotQueue() {
        return new Queue(PAYMENT_EVENT_PARKING_LOT_QUEUE, true);
    }

    @Bean
    public Binding paymentSucceededBinding(@Qualifier("paymentSucceededQueue") Queue paymentSucceededQueue,
                                           @Qualifier("paymentEventExchange") DirectExchange paymentEventExchange) {
        return BindingBuilder.bind(paymentSucceededQueue).to(paymentEventExchange).with(PAYMENT_SUCCEEDED_ROUTING_KEY);
    }

    @Bean
    public Binding duplicatePaymentBinding(@Qualifier("duplicatePaymentQueue") Queue duplicatePaymentQueue,
                                           @Qualifier("paymentEventExchange") DirectExchange paymentEventExchange) {
        return BindingBuilder.bind(duplicatePaymentQueue).to(paymentEventExchange).with(DUPLICATE_PAYMENT_ROUTING_KEY);
    }

    @Bean
    public Binding latePaymentBinding(@Qualifier("latePaymentQueue") Queue latePaymentQueue,
                                      @Qualifier("paymentEventExchange") DirectExchange paymentEventExchange) {
        return BindingBuilder.bind(latePaymentQueue).to(paymentEventExchange).with(LATE_PAYMENT_ROUTING_KEY);
    }

    @Bean
    public Binding paymentSucceededFailureBinding(@Qualifier("paymentSucceededFailureQueue") Queue paymentSucceededFailureQueue,
                                                  @Qualifier("paymentEventFailureExchange") DirectExchange paymentEventFailureExchange) {
        return BindingBuilder.bind(paymentSucceededFailureQueue).to(paymentEventFailureExchange)
            .with(PAYMENT_SUCCEEDED_FAILURE_ROUTING_KEY);
    }

    @Bean
    public Binding duplicatePaymentFailureBinding(@Qualifier("duplicatePaymentFailureQueue") Queue duplicatePaymentFailureQueue,
                                                  @Qualifier("paymentEventFailureExchange") DirectExchange paymentEventFailureExchange) {
        return BindingBuilder.bind(duplicatePaymentFailureQueue).to(paymentEventFailureExchange)
            .with(DUPLICATE_PAYMENT_FAILURE_ROUTING_KEY);
    }

    @Bean
    public Binding latePaymentFailureBinding(@Qualifier("latePaymentFailureQueue") Queue latePaymentFailureQueue,
                                             @Qualifier("paymentEventFailureExchange") DirectExchange paymentEventFailureExchange) {
        return BindingBuilder.bind(latePaymentFailureQueue).to(paymentEventFailureExchange)
            .with(LATE_PAYMENT_FAILURE_ROUTING_KEY);
    }

    @Bean
    public Binding paymentEventParkingLotBinding(@Qualifier("paymentEventParkingLotQueue") Queue paymentEventParkingLotQueue,
                                                 @Qualifier("paymentEventFailureExchange") DirectExchange paymentEventFailureExchange) {
        return BindingBuilder.bind(paymentEventParkingLotQueue).to(paymentEventFailureExchange)
            .with(PAYMENT_EVENT_PARKING_LOT_ROUTING_KEY);
    }

    private Queue eventQueue(String queueName, String failureRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", PAYMENT_EVENT_FAILURE_EXCHANGE);
        args.put("x-dead-letter-routing-key", failureRoutingKey);
        return new Queue(queueName, true, false, false, args);
    }
}
