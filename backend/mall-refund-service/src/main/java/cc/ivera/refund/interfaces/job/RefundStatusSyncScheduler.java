package cc.ivera.refund.interfaces.job;

import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退款状态同步兜底调度器。
 * <p>
 * 背景：退款状态同步依赖 MQ 延迟消息（RefundStatusSyncConsumer）触发，若 MQ 不可用或消息重试耗尽，
 * 退款单可能滞留 PROCESSING。本任务作为 DB 驱动的兜底：周期性扫描"已审核通过且处理中"的退款单，
 * 调用既有 queryRefundStatus 链路向渠道对账。
 * <p>
 * 幂等性：queryRefundStatus 按退款单号加分布式锁，内部为状态 CAS 更新（updateRefundIfStatusIn），
 * 与 MQ 消费并发执行安全，重复对账无副作用。
 */
@Component
@Slf4j
public class RefundStatusSyncScheduler {

    private final RefundInfoRepository refundInfoRepository;

    private final RefundApplicationService refundApplicationService;

    public RefundStatusSyncScheduler(RefundInfoRepository refundInfoRepository,
                                     RefundApplicationService refundApplicationService) {
        this.refundInfoRepository = refundInfoRepository;
        this.refundApplicationService = refundApplicationService;
    }

    /**
     * DB 最终一致性扫描周期独立于 MQ 延迟时间，默认 60s。
     * 即使 MQ 不可用、重试耗尽进入 Failure Queue，或消息未能及时消费，仍按数据库状态持续收敛。
     */
    @Scheduled(fixedDelayString = "${payment.refund.status-sync-scan-ms:60000}")
    public void scan() {
        List<RefundInfo> refunds = refundInfoRepository.listProcessingApproved();
        for (RefundInfo refundInfo : refunds) {
            try {
                refundApplicationService.queryRefundStatus(refundInfo.getRefundNo());
            } catch (RuntimeException e) {
                log.warn("退款状态同步兜底失败，等待下轮重试或人工处理，refundNo={}", refundInfo.getRefundNo(), e);
            }
        }
    }
}
