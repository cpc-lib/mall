package cc.ivera.service.impl;

import cc.ivera.config.RefundStatusSyncRabbitConfig;
import cc.ivera.mq.RefundStatusSyncMessage;
import cc.ivera.service.RefundStatusSyncMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class RefundStatusSyncMessageServiceImpl implements RefundStatusSyncMessageService {

    private final RabbitTemplate rabbitTemplate;

    public RefundStatusSyncMessageServiceImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendRefundStatusSyncMessage(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            throw new IllegalArgumentException("refundNo must not be blank");
        }

        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo(refundNo);

        rabbitTemplate.convertAndSend(
                RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_EVENT_EXCHANGE,
                RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_DELAY_ROUTING_KEY,
                message);
        log.info("Refund status sync message sent, refundNo={}", refundNo);
    }
}

