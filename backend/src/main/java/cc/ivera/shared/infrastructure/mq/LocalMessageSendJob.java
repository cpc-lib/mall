package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.mq.LocalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 本地消息表（事务性发件箱）兜底投递任务：
 * 周期扫描 PENDING 消息重新投递（指数回避），兜住 afterCommit 投递失败、
 * broker 确认超时、应用重启丢发等场景；投递重试超限由服务置 FAILED 待人工补偿。
 */
@Component
@Slf4j
public class LocalMessageSendJob {

    private final LocalMessageService localMessageService;

    public LocalMessageSendJob(LocalMessageService localMessageService) {
        this.localMessageService = localMessageService;
    }

    @Scheduled(fixedDelay = 30000)
    public void publishPendingMessages() {
        localMessageService.publishPending();
    }
}
