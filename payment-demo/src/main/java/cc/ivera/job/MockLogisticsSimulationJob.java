package cc.ivera.job;

import cc.ivera.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 模拟物流推进任务（V2）：周期性把到期运单 SHIPPED→IN_TRANSIT→DELIVERED。
 * 全部 CAS 更新，天然幂等；异常吞掉不影响下一轮。
 */
@Component
@Slf4j
public class MockLogisticsSimulationJob {

    private final ShipmentService shipmentService;

    public MockLogisticsSimulationJob(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Scheduled(fixedDelayString = "${logistics.mock.advance-ms:15000}")
    public void advance() {
        try {
            shipmentService.advanceMockLogistics();
        } catch (RuntimeException e) {
            log.error("模拟物流推进失败，等待下一轮重试", e);
        }
    }
}
