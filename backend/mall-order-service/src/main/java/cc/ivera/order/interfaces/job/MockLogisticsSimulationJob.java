package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.ShipmentService;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "logistics.mock.enabled", matchIfMissing = true)
public class MockLogisticsSimulationJob {
    private static final Logger log = LoggerFactory.getLogger(MockLogisticsSimulationJob.class);
    private static final String LOCK_KEY = "scheduler:mock-logistics";

    private final ShipmentService shipmentService;
    private final DistributedLockTemplate lockTemplate;

    public MockLogisticsSimulationJob(ShipmentService shipmentService,
                                      DistributedLockTemplate lockTemplate) {
        this.shipmentService = shipmentService;
        this.lockTemplate = lockTemplate;
    }

    @Scheduled(fixedDelayString = "${logistics.mock.advance-ms:15000}")
    public void advance() {
        if (!lockTemplate.tryExecute(LOCK_KEY, () -> {
            try {
                shipmentService.advanceMockLogistics();
            } catch (RuntimeException e) {
                log.error("模拟物流推进失败，等待下一轮重试", e);
            }
        })) {
            log.debug("MockLogisticsSimulationJob 抢锁失败，跳过本轮");
        }
    }
}
