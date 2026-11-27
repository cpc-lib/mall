package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.SagaHistoryCleanupService;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 定期清理超过保留期的成功终态 Saga 实例。
 * <ul>
 *   <li>默认关闭（order.saga-history.clean-enabled=false），运维确认备份后通过 Nacos 显式开启；</li>
 *   <li>多实例用分布式锁串行，避免重复清理；</li>
 *   <li>单批失败仅记录日志并停止本轮，等下一轮调度，不影响其它批次；</li>
 *   <li>仅清理 status=SU、compensation_status 为空、未运行、无子实例的根实例，非成功状态永不自动清理。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.saga-history", name = "clean-enabled", havingValue = "true")
public class SagaHistoryCleanupScheduler {

    private static final String LOCK_KEY = "scheduler:saga-history-cleanup";

    private final SagaHistoryCleanupService cleanupService;
    private final DistributedLockTemplate lockTemplate;
    private final long retentionDays;

    public SagaHistoryCleanupScheduler(SagaHistoryCleanupService cleanupService,
                                       DistributedLockTemplate lockTemplate,
                                       @Value("${order.saga-history.retention-days:30}") long retentionDays) {
        this.cleanupService = cleanupService;
        this.lockTemplate = lockTemplate;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${order.saga-history.clean-scan-ms:600000}")
    public void clean() {
        if (!lockTemplate.tryExecute(LOCK_KEY, () -> {
            Date cutoff = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays));
            int total = 0;
            while (true) {
                try {
                    int cleaned = cleanupService.cleanBatch(cutoff);
                    if (cleaned == 0) {
                        break;
                    }
                    total += cleaned;
                } catch (RuntimeException ex) {
                    log.error("Seata Saga 历史实例清理批次失败，本轮停止，等待下一轮调度重试", ex);
                    break;
                }
            }
            if (total > 0) {
                log.info("Seata Saga 历史实例清理完成：本轮删除 {} 条超过 {} 天的成功终态实例", total, retentionDays);
            }
        })) {
            log.debug("SagaHistoryCleanupScheduler 抢锁失败，跳过本轮清理");
        }
    }
}
