package cc.ivera.refund.interfaces.mq;

import cc.ivera.refund.application.ExceptionRefundService;
import cc.ivera.shared.domain.mq.ConsumerDedupeService;
import cc.ivera.shared.domain.mq.DuplicatePaymentMessage;
import cc.ivera.shared.domain.mq.LatePaymentMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 异常支付冲正消费者：消费重复支付与晚到支付事件，按 paymentNo 幂等发起原路退款。
 * 消费顺序固定为：业务 → 消费去重回写 → basicAck。
 */
@Component
@Slf4j
public class ExceptionPaymentRefundConsumer {

    static final String DUPLICATE_CONSUMER = "refund.duplicate-payment";

    static final String LATE_CONSUMER = "refund.late-payment";

    private final ExceptionRefundService exceptionRefundService;

    private final ConsumerDedupeService consumerDedupeService;

    public ExceptionPaymentRefundConsumer(ExceptionRefundService exceptionRefundService,
                                          ConsumerDedupeService consumerDedupeService) {
        this.exceptionRefundService = exceptionRefundService;
        this.consumerDedupeService = consumerDedupeService;
    }

    @RabbitListener(queues = "payment.duplicate.refund.queue")
    public void handleDuplicatePayment(DuplicatePaymentMessage message,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null || !StringUtils.hasText(message.getPaymentNo())) {
            log.warn("收到无效的重复支付消息，忽略处理，message={}", message);
            channel.basicAck(deliveryTag, false);
            return;
        }
        String eventId = StringUtils.hasText(message.getEventId()) ? message.getEventId() : message.getPaymentNo();
        if (consumerDedupeService.isConsumed(DUPLICATE_CONSUMER, eventId)) {
            log.info("重复支付事件重复投递，忽略，eventId={}", eventId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        exceptionRefundService.duplicatePayment(message.getPaymentNo());

        consumerDedupeService.markConsumed(DUPLICATE_CONSUMER, eventId);
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "payment.late.refund.queue")
    public void handleLatePayment(LatePaymentMessage message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null || !StringUtils.hasText(message.getPaymentNo())) {
            log.warn("收到无效的晚到支付消息，忽略处理，message={}", message);
            channel.basicAck(deliveryTag, false);
            return;
        }
        String eventId = StringUtils.hasText(message.getEventId()) ? message.getEventId() : message.getPaymentNo();
        if (consumerDedupeService.isConsumed(LATE_CONSUMER, eventId)) {
            log.info("晚到支付事件重复投递，忽略，eventId={}", eventId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        exceptionRefundService.latePayment(message.getPaymentNo());

        consumerDedupeService.markConsumed(LATE_CONSUMER, eventId);
        channel.basicAck(deliveryTag, false);
    }
}
