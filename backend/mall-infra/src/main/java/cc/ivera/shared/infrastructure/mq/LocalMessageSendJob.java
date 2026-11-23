package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.domain.mq.LocalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本地消息表（事务性发件箱）兜底投递任务：
 * 周期扫描 PENDING 消息重新投递（指数回避），兜住 afterCommit 投递失败、
 * broker 确认超时、应用重启丢发等场景；投递重试超限由服务置 FAILED 待人工补偿。
 *
 * <p>按领域拆分后每个进程只中继自己产生的 biz_type（local-message.relay-biz-types），
 * 并各自持有按应用名隔离的锁，避免共用单键时每轮只有一个进程能扫描、其它域消息饿死。</p>
 */
@Component
@Slf4j
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "local-message", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
public class LocalMessageSendJob {

    private static final String LOCK_KEY_PREFIX = "scheduler:local-message-send:";

    private final LocalMessageService localMessageService;
    private final DistributedLockTemplate lockTemplate;
    private final List<String> relayBizTypes;
    private final String lockKey;

    public LocalMessageSendJob(LocalMessageService localMessageService,
                               DistributedLockTemplate lockTemplate,
                               @Value("${local-message.relay-biz-types:}") String relayBizTypes,
                               @Value("${spring.application.name:local-message-relay}") String applicationName) {
        this.localMessageService = localMessageService;
        this.lockTemplate = lockTemplate;
        this.relayBizTypes = parseBizTypes(relayBizTypes);
        this.lockKey = LOCK_KEY_PREFIX + applicationName;
    }

    @Scheduled(fixedDelay = 30000)
    public void publishPendingMessages() {
        if (!lockTemplate.tryExecute(lockKey, () -> localMessageService.publishPending(relayBizTypes))) {
            log.debug("LocalMessageSendJob 抢锁失败，跳过本轮投递，lockKey={}", lockKey);
        }
    }

    private static List<String> parseBizTypes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toList());
    }
}
