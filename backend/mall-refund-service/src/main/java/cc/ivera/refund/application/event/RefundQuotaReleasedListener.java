package cc.ivera.refund.application.event;

import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 退款冻结额度释放事件监听器。
 * <p>
 * 仅当退款单仍处于 REFUNDING / APPROVED 时才置 FAILED，避免覆盖后续链路
 * （渠道退款成功 / 重复支付冲正等）已推进的状态。
 * 改为 Repository 条件 UPDATE，不再先读后改，防止锁外脏对象覆盖。
 */
@Component
@Slf4j
public class RefundQuotaReleasedListener {
    private final RefundOrderRepository refundOrderRepository;

    public RefundQuotaReleasedListener(RefundOrderRepository refundOrderRepository) {
        this.refundOrderRepository = refundOrderRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void release(RefundQuotaReleasedEvent event) {
        int rows = refundOrderRepository.markFailedIfStatusIn(event.getRefundNo(),
            List.of(RefundOrderStatus.REFUNDING.getType(), RefundOrderStatus.APPROVED.getType()));
        if (rows == 0) {
            log.debug("RefundQuotaReleasedListener 跳过：退款单已被其他链路推进，refundNo={}", event.getRefundNo());
        } else {
            log.info("RefundQuotaReleasedListener 置 FAILED 成功，refundNo={}", event.getRefundNo());
        }
    }
}
