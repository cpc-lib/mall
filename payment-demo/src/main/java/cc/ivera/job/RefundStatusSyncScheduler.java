package cc.ivera.job;

import cc.ivera.entity.RefundInfo;
import cc.ivera.enums.RefundApprovalStatus;
import cc.ivera.enums.RefundStatus;
import cc.ivera.mapper.RefundInfoMapper;
import cc.ivera.service.RefundApplicationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退款状态同步兜底调度器。
 *
 * 背景：退款状态同步依赖 MQ 延迟消息（RefundStatusSyncConsumer）触发，若 MQ 不可用或消息重试耗尽，
 * 退款单可能滞留 PROCESSING。本任务作为 DB 驱动的兜底：周期性扫描"已审核通过且处理中"的退款单，
 * 调用既有 queryRefundStatus 链路向渠道对账。
 *
 * 幂等性：queryRefundStatus 按退款单号加分布式锁，内部为状态 CAS 更新（updateRefundIfStatusIn），
 * 与 MQ 消费并发执行安全，重复对账无副作用。
 */
@Component
@Slf4j
public class RefundStatusSyncScheduler {

    private final RefundInfoMapper refundInfoMapper;

    private final RefundApplicationService refundApplicationService;

    public RefundStatusSyncScheduler(RefundInfoMapper refundInfoMapper, RefundApplicationService refundApplicationService) {
        this.refundInfoMapper = refundInfoMapper;
        this.refundApplicationService = refundApplicationService;
    }

    /** 扫描间隔与 MQ 延迟关单 TTL 共用 payment.refund.status-sync-delay-ms（默认 60s）。 */
    @Scheduled(fixedDelayString = "${payment.refund.status-sync-delay-ms:60000}")
    public void scan() {
        QueryWrapper<RefundInfo> q = new QueryWrapper<>();
        q.eq("approval_status", RefundApprovalStatus.APPROVED.getType())
                .eq("refund_status", RefundStatus.PROCESSING.getType())
                .orderByAsc("create_time")
                .last("limit 100");
        List<RefundInfo> refunds;
        try {
            refunds = refundInfoMapper.selectList(q);
        } catch (RuntimeException dmLimitSyntax) {
            // DM8 对 LIMIT 语法兼容性依赖模式；退化为不带 LIMIT 的扫描，不改变业务时序。
            q = new QueryWrapper<>();
            q.eq("approval_status", RefundApprovalStatus.APPROVED.getType())
                    .eq("refund_status", RefundStatus.PROCESSING.getType())
                    .orderByAsc("create_time");
            refunds = refundInfoMapper.selectList(q);
        }
        for (RefundInfo refundInfo : refunds) {
            try {
                refundApplicationService.queryRefundStatus(refundInfo.getRefundNo());
            } catch (RuntimeException e) {
                log.warn("退款状态同步兜底失败，等待下轮重试或人工处理，refundNo={}", refundInfo.getRefundNo(), e);
            }
        }
    }
}
