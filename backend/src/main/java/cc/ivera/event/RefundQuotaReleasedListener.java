package cc.ivera.event;

import cc.ivera.entity.RefundOrder;
import cc.ivera.enums.RefundOrderStatus;
import cc.ivera.mapper.RefundOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RefundQuotaReleasedListener {
    private final RefundOrderMapper applyMapper;
    public RefundQuotaReleasedListener(RefundOrderMapper applyMapper) { this.applyMapper = applyMapper; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void release(RefundQuotaReleasedEvent event) {
        RefundOrder apply = applyMapper.selectOne(new QueryWrapper<RefundOrder>().eq("refund_no", event.getRefundNo()));
        if (apply != null && (RefundOrderStatus.REFUNDING.getType().equals(apply.getStatus())
                || RefundOrderStatus.APPROVED.getType().equals(apply.getStatus()))) {
            // REFUNDING：渠道退款失败/关闭，置 FAILED 可重试；冻结额度保持占用，重试成功后结转
            apply.setStatus(RefundOrderStatus.FAILED.getType());
            apply.setLegacyApplyStatus("FAILED");
            applyMapper.updateById(apply);
        }
    }
}
