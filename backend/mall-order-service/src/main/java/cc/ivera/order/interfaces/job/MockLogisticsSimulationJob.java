package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MockLogisticsSimulationJob {
    private static final Logger log = LoggerFactory.getLogger(MockLogisticsSimulationJob.class);
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
