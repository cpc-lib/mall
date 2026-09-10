package cc.ivera.shared.infrastructure.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 消息投递可靠性配置（三要素 + 兜底）：
 * - 发送者确认：publisher-confirm-type=correlated，回调确认消息到达交换机，失败打错误日志；
 * - 发送者退回：publisher-returns + template.mandatory=true，消息不可路由到队列时打错误日志；
 * - 消费者确认：listener acknowledge-mode=auto，监听器成功后 ack，异常按重试策略处理；重试耗尽后 release queue 通过 DLX 进入独立 failure queue；
 * - 持久化：交换机/队列声明 durable=true，Spring AMQP 消息默认 PERSISTENT；
 * - 兜底：MQ failure queue / parking-lot queue 用于故障审计与人工隔离，业务状态仍由 DB 驱动的兜底定时任务对账收敛
 * （TimeoutOrderCloseScheduler / RefundStatusSyncScheduler，链路均为幂等 CAS）。
 */
@Configuration
@Slf4j
public class RabbitReliabilityConfig {

    private final RabbitTemplate rabbitTemplate;

    public RabbitReliabilityConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ 发送者确认失败：消息未到达交换机，correlationData={}，cause={}，业务由兜底定时任务对账",
                    correlationData == null ? null : correlationData.getId(), cause);
            }
        });
        // Boot 2.3.7 对应 spring-rabbit 2.2.x：退回回调为五参数 setReturnCallback（ReturnedMessage 为 2.3+ API）。
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) ->
            log.error("RabbitMQ 消息不可路由被退回：exchange={}，routingKey={}，replyText={}，业务由兜底定时任务对账",
                exchange, routingKey, replyText));
    }
}
