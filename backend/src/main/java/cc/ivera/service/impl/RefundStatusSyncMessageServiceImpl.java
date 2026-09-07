package cc.ivera.service.impl;

import cc.ivera.entity.LocalMessage;
import cc.ivera.mq.RefundStatusSyncMessage;
import cc.ivera.service.LocalMessageService;
import cc.ivera.service.RefundStatusSyncMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class RefundStatusSyncMessageServiceImpl implements RefundStatusSyncMessageService {

    private final LocalMessageService localMessageService;

    public RefundStatusSyncMessageServiceImpl(LocalMessageService localMessageService) {
        this.localMessageService = localMessageService;
    }

    @Override
    public void sendRefundStatusSyncMessage(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            throw new IllegalArgumentException("refundNo must not be blank");
        }

        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo(refundNo);

        // 事务性发件箱：落库 PENDING，立即（或事务提交后）投递 MQ（发送者确认），消费成功后由消费者回写 CONSUMED
        localMessageService.saveAndPublishAfterCommit(LocalMessage.BIZ_TYPE_REFUND_SYNC, refundNo, message);
        log.info("Refund status sync message saved to local message table, refundNo={}", refundNo);
    }
}
