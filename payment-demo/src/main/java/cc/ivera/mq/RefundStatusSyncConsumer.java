package cc.ivera.mq;

import cc.ivera.config.RefundStatusSyncRabbitConfig;
import cc.ivera.service.RefundApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class RefundStatusSyncConsumer {

    private final RefundApplicationService refundApplicationService;

    public RefundStatusSyncConsumer(RefundApplicationService refundApplicationService) {
        this.refundApplicationService = refundApplicationService;
    }

    @RabbitListener(queues = RefundStatusSyncRabbitConfig.REFUND_STATUS_SYNC_RELEASE_QUEUE)
    public void handleRefundStatusSync(RefundStatusSyncMessage message) {
        if (message == null || !StringUtils.hasText(message.getRefundNo())) {
            log.warn("Received empty refund status sync message, ignored");
            return;
        }

        String refundNo = message.getRefundNo();
        log.info("Start refund status sync message, refundNo={}", refundNo);
        refundApplicationService.queryRefundStatus(refundNo);
        log.info("Finished refund status sync message, refundNo={}", refundNo);
    }
}

