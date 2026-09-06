package cc.ivera.event;

import cc.ivera.service.RefundOrderService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 渠道退款成功后结转（V2）：
 * RefundOrder→SUCCESS；Order/OrderItem/PaymentOrder 冻结→已退（settle 幂等）；
 * 库存回补不在结转时进行——由退款类型策略在受理/签收时调用 InventoryService.restockForRefund。
 */
@Component
public class RefundSucceededListener {
    private final RefundOrderService refundOrderService;
    public RefundSucceededListener(RefundOrderService refundOrderService){this.refundOrderService=refundOrderService;}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void onSuccess(RefundSucceededEvent event){
        try {
            refundOrderService.settle(event.getRefundNo());
        } catch (RuntimeException e) {
            // 结转失败不阻塞渠道回调应答；settle 幂等可由对账/重试补偿
            org.slf4j.LoggerFactory.getLogger(RefundSucceededListener.class)
                    .error("退款结转失败，待补偿重试，refundNo={}", event.getRefundNo(), e);
        }
    }
}
