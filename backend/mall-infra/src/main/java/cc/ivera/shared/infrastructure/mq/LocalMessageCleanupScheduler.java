package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 清理已消费的 Outbox 与跨上下文消费去重记录：二者只服务于投递/消费幂等，
 * 超过保留窗口后删除不影响业务事实与状态推进。分批（LIMIT 500）删除，
 * 单批失败只记录日志，等待下一轮继续收敛。
 * <p>与 {@link LocalMessageSendJob} 共用 {@code local-message.relay-enabled} 开关；
 * 多进程共用同一把全局锁，保证每轮只有一个实例执行清理。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "local-message", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
public class LocalMessageCleanupScheduler {

    private static final String LOCK_KEY = "scheduler:local-message-cleanup";
    private static final int CLEAN_LIMIT = 500;
    private static final long LOCAL_MESSAGE_RETENTION_DAYS = 7L;
    private static final long DEDUPE_RETENTION_DAYS = 30L;

    private final LocalMessageMapper localMessageMapper;
    private final ConsumerDedupeMapper consumerDedupeMapper;
    private final DistributedLockTemplate lockTemplate;

    public LocalMessageCleanupScheduler(LocalMessageMapper localMessageMapper,
                                        ConsumerDedupeMapper consumerDedupeMapper,
                                        DistributedLockTemplate lockTemplate) {
        this.localMessageMapper = localMessageMapper;
        this.consumerDedupeMapper = consumerDedupeMapper;
        this.lockTemplate = lockTemplate;
    }

    @Scheduled(fixedDelayString = "${local-message.clean-scan-ms:600000}")
    public void clean() {
        if (!lockTemplate.tryExecute(LOCK_KEY, () -> {
            Date now = new Date();
            try {
                int consumed = deleteConsumed(now);
                if (consumed > 0) {
                    log.info("清理已消费 Outbox 记录 {} 条", consumed);
                }
            } catch (RuntimeException e) {
                log.error("清理已消费 Outbox 记录失败", e);
            }
            try {
                int deduped = deleteDedupe(now);
                if (deduped > 0) {
                    log.info("清理过期消费去重记录 {} 条", deduped);
                }
            } catch (RuntimeException e) {
                log.error("清理过期消费去重记录失败", e);
            }
        })) {
            log.debug("LocalMessageCleanupScheduler 抢锁失败，跳过本轮清理");
        }
    }

    private int deleteConsumed(Date now) {
        Date before = new Date(now.getTime() - TimeUnit.DAYS.toMillis(LOCAL_MESSAGE_RETENTION_DAYS));
        List<LocalMessage> rows = localMessageMapper.selectList(new LambdaQueryWrapper<LocalMessage>()
            .select(LocalMessage::getId)
            .eq(LocalMessage::getStatus, LocalMessage.STATUS_CONSUMED)
            .lt(LocalMessage::getUpdateTime, before)
            .orderByAsc(LocalMessage::getId)
            .last("limit " + CLEAN_LIMIT));
        if (rows.isEmpty()) return 0;
        return localMessageMapper.deleteBatchIds(rows.stream().map(LocalMessage::getId).toList());
    }

    private int deleteDedupe(Date now) {
        Date before = new Date(now.getTime() - TimeUnit.DAYS.toMillis(DEDUPE_RETENTION_DAYS));
        List<ConsumerDedupePO> rows = consumerDedupeMapper.selectList(new LambdaQueryWrapper<ConsumerDedupePO>()
            .select(ConsumerDedupePO::getId)
            .lt(ConsumerDedupePO::getCreateTime, before)
            .orderByAsc(ConsumerDedupePO::getId)
            .last("limit " + CLEAN_LIMIT));
        if (rows.isEmpty()) return 0;
        return consumerDedupeMapper.deleteBatchIds(rows.stream().map(ConsumerDedupePO::getId).toList());
    }
}
