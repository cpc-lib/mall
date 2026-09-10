package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelOrderStatusDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class TimeoutOrderCloseScheduler {
    private final OrderInfoService orderInfoService;
    private final ChannelOrderStatusDispatcher channelOrderStatusDispatcher;

    /**
     * 本地订单未支付超时（分钟），与下单 expire_time、MQ 延迟关单 TTL 共用同一业务超时配置。
     * Scheduler 自身扫描周期由 payment.order.timeout-scan-ms 独立控制，作为 DB 最终一致性兜底。
     */
    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    public TimeoutOrderCloseScheduler(OrderInfoService orderInfoService, ChannelOrderStatusDispatcher channelOrderStatusDispatcher) {
        this.orderInfoService = orderInfoService;
        this.channelOrderStatusDispatcher = channelOrderStatusDispatcher;
    }

    @Scheduled(fixedDelayString = "${payment.order.timeout-scan-ms:60000}")
    public void scan() {
        Date cutoff = new Date(System.currentTimeMillis() - orderExpireMinutes * 60L * 1000L);
        List<OrderInfo> orders = orderInfoService.listTimeoutNotPayOrders(cutoff);
        for (OrderInfo order : orders) {
            try {
                // 绝不直接改本地状态：必须让渠道服务完成“查渠道 -> 必要时关渠道 -> 再条件更新本地”。
                channelOrderStatusDispatcher.checkOrderStatus(order.getPaymentType(), order.getOrderNo());
            } catch (RuntimeException e) {
                log.error("超时关单对账失败，保持本地订单原状态等待下轮重试，orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
