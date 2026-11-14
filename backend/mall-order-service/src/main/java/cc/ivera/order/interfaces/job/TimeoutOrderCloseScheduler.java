package cc.ivera.order.interfaces.job;

import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelOrderStatusApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class TimeoutOrderCloseScheduler {
    private static final Logger log = LoggerFactory.getLogger(TimeoutOrderCloseScheduler.class);
    private final OrderInfoService orderInfoService;
    private final ChannelOrderStatusApi channelOrderStatusDispatcher;
    private final PaymentOrderQueryApi paymentOrderQueryApi;

    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    public TimeoutOrderCloseScheduler(OrderInfoService orderInfoService,
                                      ChannelOrderStatusApi channelOrderStatusDispatcher,
                                      PaymentOrderQueryApi paymentOrderQueryApi) {
        this.orderInfoService = orderInfoService;
        this.channelOrderStatusDispatcher = channelOrderStatusDispatcher;
        this.paymentOrderQueryApi = paymentOrderQueryApi;
    }

    @Scheduled(fixedDelayString = "${payment.order.timeout-scan-ms:60000}")
    public void scan() {
        Date cutoff = new Date(System.currentTimeMillis() - orderExpireMinutes * 60_000L);
        List<OrderInfo> orders = orderInfoService.listTimeoutNotPayOrders(cutoff);
        for (OrderInfo order : orders) {
            try {
                if (paymentOrderQueryApi.hasSuccessfulPayment(order.getOrderNo())) {
                    log.info("超时订单存在有效支付成交，转为支付成功处理，orderNo={}", order.getOrderNo());
                    orderInfoService.markPaid(order.getOrderNo());
                    continue;
                }
                channelOrderStatusDispatcher.checkOrderStatus(order.getPaymentType(), order.getOrderNo());
            } catch (RuntimeException e) {
                log.error("超时关单对账失败，保持本地订单原状态等待下轮重试，orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
