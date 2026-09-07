package cc.ivera.service.logistics;

import cc.ivera.entity.OrderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 模拟物流商实现（V2）：
 * - 运单号：MOCK + 时间戳 + 4 位随机段；
 * - 状态推进由 MockLogisticsSimulationJob 定时 CAS 完成（SHIPPED→IN_TRANSIT→DELIVERED），
 *   本类只负责建运单与对外查询。
 */
@Component
@Slf4j
public class MockLogisticsProvider implements LogisticsProvider {

    private static final Random RANDOM = new Random();

    @Override
    public String createWaybill(OrderInfo order) {
        if (order == null || order.getOrderNo() == null) {
            throw new IllegalArgumentException("创建模拟运单缺少订单信息");
        }
        String trackingNo = "MOCK" + System.currentTimeMillis() + String.format("%04d", RANDOM.nextInt(10000));
        log.info("模拟物流商创建运单，orderNo={}, trackingNo={}", order.getOrderNo(), trackingNo);
        return trackingNo;
    }

    @Override
    public String queryStatus(String trackingNo) {
        // 模拟物流商侧仅回执"已揽收，运输中"；本地权威状态以 t_order_shipment 为准。
        return "IN_TRANSIT";
    }
}
