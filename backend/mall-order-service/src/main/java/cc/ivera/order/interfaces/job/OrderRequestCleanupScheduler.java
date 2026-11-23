package cc.ivera.order.interfaces.job;

import cc.ivera.order.domain.repository.OrderRequestRepository;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 清理过期的下单幂等记录：仅治理 t_order_request 体积，与交易状态无关。
 */
@Component
public class OrderRequestCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderRequestCleanupScheduler.class);
    private static final String LOCK_KEY = "scheduler:order-request-cleanup";
    private static final int REQUEST_CLEAN_LIMIT = 500;

    private final OrderRequestRepository orderRequestRepository;
    private final DistributedLockTemplate lockTemplate;

    public OrderRequestCleanupScheduler(OrderRequestRepository orderRequestRepository,
                                        DistributedLockTemplate lockTemplate) {
        this.orderRequestRepository = orderRequestRepository;
        this.lockTemplate = lockTemplate;
    }

    @Scheduled(fixedDelayString = "${order.request.clean-scan-ms:60000}")
    public void clean() {
        if (!lockTemplate.tryExecute(LOCK_KEY, () -> {
            try {
                int cleaned = orderRequestRepository.deleteExpired(new Date(), REQUEST_CLEAN_LIMIT);
                if (cleaned > 0) {
                    log.info("清理过期下单幂等记录 {} 条", cleaned);
                }
            } catch (RuntimeException e) {
                log.error("清理过期下单幂等记录失败", e);
            }
        })) {
            log.debug("OrderRequestCleanupScheduler 抢锁失败，跳过本轮清理");
        }
    }
}
