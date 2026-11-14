package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.saga.OrderSagaService;
import cc.ivera.order.domain.model.SagaInstance;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.order.domain.repository.OrderRequestRepository;
import cc.ivera.order.domain.repository.SagaInstanceRepository;
import cc.ivera.product.application.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 下单 Saga 最终一致性兜底：扫描长时间停留在 PROCESSING 的实例，
 * 按本地订单是否落库收敛为 SUCCEEDED 或（释放预占后）FAILED；
 * 单条失败只安排退避重试并记录日志，不中断同批次其它记录。
 */
@Component
public class OrderSagaRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaRecoveryScheduler.class);
    private static final int BATCH_LIMIT = 100;
    private static final int REQUEST_CLEAN_LIMIT = 500;
    private static final String STEP_RECOVERY = "RECOVERY";

    private final SagaInstanceRepository sagaInstanceRepository;
    private final OrderSagaService orderSagaService;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OrderRequestRepository orderRequestRepository;

    /**
     * 判定 Saga 卡住的阈值（毫秒）：正常下单远小于该值，超过则视为进程中断残留。
     */
    @Value("${order.saga.recovery-delay-ms:300000}")
    private long recoveryDelayMs;

    public OrderSagaRecoveryScheduler(SagaInstanceRepository sagaInstanceRepository,
                                      OrderSagaService orderSagaService,
                                      OrderRepository orderRepository,
                                      InventoryService inventoryService,
                                      OrderRequestRepository orderRequestRepository) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.orderSagaService = orderSagaService;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.orderRequestRepository = orderRequestRepository;
    }

    @Scheduled(fixedDelayString = "${order.saga.recovery-scan-ms:60000}")
    public void recover() {
        Date cutoff = new Date(System.currentTimeMillis() - recoveryDelayMs);
        List<SagaInstance> sagas = sagaInstanceRepository.listRecoverable(cutoff, BATCH_LIMIT);
        for (SagaInstance saga : sagas) {
            try {
                recoverOne(saga);
            } catch (RuntimeException e) {
                int retryCount = saga.getRetryCount() == null ? 1 : saga.getRetryCount() + 1;
                long backoff = recoveryDelayMs * (1L << Math.min(retryCount, 4));
                orderSagaService.scheduleRetry(saga.getSagaNo(), retryCount,
                    new Date(System.currentTimeMillis() + backoff));
                log.error("下单 Saga 恢复失败，安排退避重试，sagaNo={}, retryCount={}", saga.getSagaNo(), retryCount, e);
            }
        }
        cleanExpiredRequests();
    }

    private void recoverOne(SagaInstance saga) {
        if (orderRepository.findByOrderNo(saga.getBizNo()) != null) {
            orderSagaService.succeed(saga.getSagaNo());
            log.info("下单 Saga 恢复：本地订单已落库，收敛为成功，sagaNo={}", saga.getSagaNo());
            return;
        }
        // 订单缺失说明落单事务未提交：释放可能残留的预占（无预占时幂等放行）后置为失败。
        inventoryService.releaseReservation(saga.getBizNo());
        orderSagaService.fail(saga.getSagaNo(), STEP_RECOVERY);
        log.warn("下单 Saga 恢复：本地订单缺失，已释放预占并收敛为失败，sagaNo={}", saga.getSagaNo());
    }

    private void cleanExpiredRequests() {
        try {
            int cleaned = orderRequestRepository.deleteExpired(new Date(), REQUEST_CLEAN_LIMIT);
            if (cleaned > 0) log.info("清理过期下单幂等记录 {} 条", cleaned);
        } catch (RuntimeException e) {
            log.error("清理过期下单幂等记录失败", e);
        }
    }
}
