package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.domain.mq.LocalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 本地消息表（事务性发件箱）兜底投递任务：
 * 周期扫描 PENDING 消息重新投递（指数回避），兜住 afterCommit 投递失败、
 * broker 确认超时、应用重启丢发等场景；投递重试超限由服务置 FAILED 待人工补偿。
 */
@Component
@Slf4j
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "local-message", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
public class LocalMessageSendJob {

    private static final String LOCK_KEY = "scheduler:local-message-send";

    private final LocalMessageService localMessageService;
    private final DistributedLockTemplate lockTemplate;

    public LocalMessageSendJob(LocalMessageService localMessageService,
                               DistributedLockTemplate lockTemplate) {
        this.localMessageService = localMessageService;
        this.lockTemplate = lockTemplate;
    }

    @Scheduled(fixedDelay = 30000)
    public void publishPendingMessages() {
        if (!lockTemplate.tryExecute(LOCK_KEY, () -> localMessageService.publishPending())) {
            log.debug("LocalMessageSendJob 抢锁失败，跳过本轮投递");
        }
    }
}
