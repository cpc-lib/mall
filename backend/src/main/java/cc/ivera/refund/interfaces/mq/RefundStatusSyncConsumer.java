package cc.ivera.refund.interfaces.mq;


import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.refund.infrastructure.config.RefundStatusSyncRabbitConfig;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.RefundStatusSyncMessage;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    public void handleRefundStatusSync(RefundStatusSyncMessage message) {
        if (message == null || !StringUtils.hasText(message.getRefundNo())) {
            log.warn("Received empty refund status sync message, ignored");
            return;
        }

        processRefundStatusSync(message);
        // 消费者成功处理（监听器正常返回后 Spring 才向 broker ack）→ 回写本地消息表为已消费
        localMessageService.markConsumed(LocalMessage.BIZ_TYPE_REFUND_SYNC, message.getRefundNo());
    }

    private void processRefundStatusSync(RefundStatusSyncMessage message) {
        String refundNo = message.getRefundNo();
        log.info("Start refund status sync message, refundNo={}", refundNo);
        refundApplicationService.queryRefundStatus(refundNo);
        log.info("Finished refund status sync message, refundNo={}", refundNo);
    }
}
