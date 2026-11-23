package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.ShipmentService;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.domain.repository.OrderShipmentRepository;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 送达后超时未手动确认时，复用确认收货链路完成履约闭环。
 * 独立于模拟物流开关；候选查询只做粗筛，最终状态与退款守卫由确认收货链路在锁内复核。
 */
@Component
@ConditionalOnProperty(name = "order.auto-receipt.enabled", matchIfMissing = true)
public class AutoReceiptScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutoReceiptScheduler.class);
    private static final String LOCK_KEY = "scheduler:auto-receipt";
    private static final int BATCH_SIZE = 200;

    private final OrderShipmentRepository orderShipmentRepository;
    private final ShipmentService shipmentService;
    private final DistributedLockTemplate lockTemplate;
    private final long timeoutMs;

    public AutoReceiptScheduler(OrderShipmentRepository orderShipmentRepository,
                                ShipmentService shipmentService,
                                DistributedLockTemplate lockTemplate,
                                @Value("${order.auto-receipt.timeout-ms:604800000}") long timeoutMs) {
        this.orderShipmentRepository = orderShipmentRepository;
        this.shipmentService = shipmentService;
        this.lockTemplate = lockTemplate;
        this.timeoutMs = timeoutMs;
    }

    @Scheduled(fixedDelayString = "${order.auto-receipt.scan-ms:60000}")
    public void scan() {
        if (!lockTemplate.tryExecute(LOCK_KEY, () -> {
            Date cutoff = new Date(System.currentTimeMillis() - timeoutMs);
            List<OrderShipment> shipments = orderShipmentRepository.listAutoReceiptCandidates(cutoff, BATCH_SIZE);
            for (OrderShipment shipment : shipments) {
                try {
                    shipmentService.confirmReceipt(shipment.getOrderNo());
                    log.info("送达超时自动确认收货完成，orderNo={}, trackingNo={}",
                        shipment.getOrderNo(), shipment.getTrackingNo());
                } catch (RuntimeException e) {
                    log.error("送达超时自动确认收货失败，等待下一轮重试，orderNo={}, trackingNo={}",
                        shipment.getOrderNo(), shipment.getTrackingNo(), e);
                }
            }
        })) {
            log.debug("AutoReceiptScheduler 抢锁失败，跳过本轮扫描");
        }
    }
}
