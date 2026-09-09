package cc.ivera.order.infrastructure.mq;

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
public class OrderCloseRabbitConfig {

    public static final String ORDER_CLOSE_EVENT_EXCHANGE = "payment.order.close.event.exchange";
    public static final String ORDER_CLOSE_DEAD_LETTER_EXCHANGE = "payment.order.close.dead-letter.exchange";
    public static final String ORDER_CLOSE_DELAY_QUEUE = "payment.order.close.delay.queue";
    public static final String ORDER_CLOSE_RELEASE_QUEUE = "payment.order.close.release.queue";
    public static final String ORDER_CLOSE_DELAY_ROUTING_KEY = "payment.order.close.delay";
    public static final String ORDER_CLOSE_RELEASE_ROUTING_KEY = "payment.order.close.release";

    @Bean(name = "orderCloseEventExchange")
    public DirectExchange orderCloseEventExchange() {
        return new DirectExchange(ORDER_CLOSE_EVENT_EXCHANGE, true, false);
    }

    @Bean(name = "orderCloseDeadLetterExchange")
    public DirectExchange orderCloseDeadLetterExchange() {
        return new DirectExchange(ORDER_CLOSE_DEAD_LETTER_EXCHANGE, true, false);
    }

    //延时处理机制（TTL 与本地订单未支付超时共用 payment.order.expire-minutes）
    @Bean(name = "orderCloseDelayQueue")
    public Queue orderCloseDelayQueue(@Value("${payment.order.expire-minutes:3}") long expireMinutes) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", ORDER_CLOSE_DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CLOSE_RELEASE_ROUTING_KEY);
        args.put("x-message-ttl", expireMinutes * 60_000L);
        return new Queue(ORDER_CLOSE_DELAY_QUEUE, true, false, false, args);
    }

    @Bean(name = "orderCloseReleaseQueue")
    public Queue orderCloseReleaseQueue() {
        return new Queue(ORDER_CLOSE_RELEASE_QUEUE, true);
    }

    @Bean
    public Binding orderCloseDelayBinding(@Qualifier("orderCloseDelayQueue") Queue orderCloseDelayQueue, @Qualifier("orderCloseEventExchange") DirectExchange orderCloseEventExchange) {
        return BindingBuilder.bind(orderCloseDelayQueue)
            .to(orderCloseEventExchange)
            .with(ORDER_CLOSE_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderCloseReleaseBinding(@Qualifier("orderCloseReleaseQueue") Queue orderCloseReleaseQueue, @Qualifier("orderCloseDeadLetterExchange") DirectExchange orderCloseDeadLetterExchange) {
        return BindingBuilder.bind(orderCloseReleaseQueue)
            .to(orderCloseDeadLetterExchange)
            .with(ORDER_CLOSE_RELEASE_ROUTING_KEY);
    }
}
