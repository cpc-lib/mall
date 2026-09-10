package cc.ivera.refund.interfaces.mq;


import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.refund.infrastructure.config.RefundStatusSyncRabbitConfig;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.RefundStatusSyncMessage;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
@Slf4j
public class RefundStatusSyncConsumer {

    private final RefundApplicationService refundApplicationService;

    private final LocalMessageService localMessageService;

    public RefundStatusSyncConsumer(RefundApplicationService refundApplicationService,
                                    LocalMessageService localMessageService) {
        this.refundApplicationService = refundApplicationService;
        this.localMessageService = localMessageService;
    }

    @RabbitListener(queues = RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_RELEASE_QUEUE)
    public void handleRefundStatusSync(RefundStatusSyncMessage message,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null || !StringUtils.hasText(message.getRefundNo())) {
            log.warn("Received empty refund status sync message, ignored");
            channel.basicAck(deliveryTag, false);
            return;
        }

        processRefundStatusSync(message);
        // MANUAL ACK：业务处理与本地消息 CONSUMED 回写全部成功后才确认 broker 消息。
        // 任一步骤异常均继续向外抛给 Spring Retry；重试耗尽后由容器 reject，进入 Failure DLQ。
        localMessageService.markConsumed(LocalMessage.BIZ_TYPE_REFUND_SYNC, message.getRefundNo());
        channel.basicAck(deliveryTag, false);
    }

    private void processRefundStatusSync(RefundStatusSyncMessage message) {
        String refundNo = message.getRefundNo();
        log.info("Start refund status sync message, refundNo={}", refundNo);
        refundApplicationService.queryRefundStatus(refundNo);
        log.info("Finished refund status sync message, refundNo={}", refundNo);
    }
}
