package cc.ivera.shared.infrastructure.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 消息投递可靠性配置（三要素 + 兜底）：
 * - 发送者确认：publisher-confirm-type=correlated，回调确认消息到达交换机，失败打错误日志；
 * - 发送者退回：publisher-returns + template.mandatory=true，消息不可路由到队列时打错误日志；
 * - 消费者确认：listener acknowledge-mode=manual，业务处理与 CONSUMED 回写成功后显式 basicAck；异常继续交给 Spring Retry，重试耗尽后 release queue 通过 DLX 进入独立 failure queue；
 * - 持久化：交换机/队列声明 durable=true，Spring AMQP 消息默认 PERSISTENT；
 * - 兜底：MQ failure queue / parking-lot queue 用于故障审计与人工隔离，业务状态仍由 DB 驱动的兜底定时任务对账收敛
 * （TimeoutOrderCloseScheduler / RefundStatusSyncScheduler，链路均为幂等 CAS）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RabbitTemplate.class)
@Slf4j
public class RabbitReliabilityConfig {

    private final RabbitTemplate rabbitTemplate;

    public RabbitReliabilityConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Java 序列化白名单：Spring AMQP 3.x 默认只放行 java.* 等基础类型，
     * 订单关单、支付事件等 cc.ivera 领域消息需显式放行，否则消费端反序列化会抛 SecurityException。
     * 声明为 static，避免 RabbitTemplate 与 MessageConverter 之间形成配置类实例化循环。
     */
    @Bean
    public static MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.addAllowedListPatterns("java.lang.*", "java.util.*", "java.math.*", "java.time.*",
            "java.io.*", "java.net.*", "cc.ivera.*");
        return converter;
    }

    @PostConstruct
    public void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ 发送者确认失败：消息未到达交换机，correlationData={}，cause={}，业务由兜底定时任务对账",
                    correlationData == null ? null : correlationData.getId(), cause);
            }
        });
        // spring-rabbit 3.x：退回回调为 setReturnsCallback（ReturnsCallback 接收 ReturnedMessage）。
        rabbitTemplate.setReturnsCallback(returned ->
            log.error("RabbitMQ 消息不可路由被退回：exchange={}，routingKey={}，replyText={}，业务由兜底定时任务对账",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}
