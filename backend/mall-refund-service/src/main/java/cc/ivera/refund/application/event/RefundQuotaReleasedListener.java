package cc.ivera.refund.application.event;

import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RefundQuotaReleasedListener {
    private final RefundOrderRepository refundOrderRepository;

    public RefundQuotaReleasedListener(RefundOrderRepository refundOrderRepository) {
        this.refundOrderRepository = refundOrderRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void release(RefundQuotaReleasedEvent event) {
        RefundOrder apply = refundOrderRepository.findByRefundNo(event.getRefundNo());
        if (apply != null && (RefundOrderStatus.REFUNDING.getType().equals(apply.getStatus())
            || RefundOrderStatus.APPROVED.getType().equals(apply.getStatus()))) {
            // REFUNDING：渠道退款失败/关闭，置 FAILED 可重试；冻结额度保持占用，重试成功后结转
            apply.setStatus(RefundOrderStatus.FAILED.getType());
            apply.setLegacyApplyStatus("FAILED");
            refundOrderRepository.update(apply);
        }
    }
}
