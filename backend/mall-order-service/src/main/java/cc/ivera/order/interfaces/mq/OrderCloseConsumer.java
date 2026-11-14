package cc.ivera.order.interfaces.mq;

import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelOrderStatusApi;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.OrderCloseMessage;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderCloseConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderCloseConsumer.class);
    private final OrderInfoService orderInfoService;
    private final ChannelOrderStatusApi channelOrderStatusDispatcher;
    private final LocalMessageService localMessageService;
    private final PaymentOrderQueryApi paymentOrderQueryApi;

    public OrderCloseConsumer(OrderInfoService orderInfoService,
                              ChannelOrderStatusApi channelOrderStatusDispatcher,
                              LocalMessageService localMessageService,
                              PaymentOrderQueryApi paymentOrderQueryApi) {
        this.orderInfoService = orderInfoService;
        this.channelOrderStatusDispatcher = channelOrderStatusDispatcher;
        this.localMessageService = localMessageService;
        this.paymentOrderQueryApi = paymentOrderQueryApi;
    }

    @RabbitListener(queues = "payment.order.close.release.queue")
    public void handleOrderClose(OrderCloseMessage message, Channel channel,
                                 @Header("amqp_deliveryTag") long deliveryTag) throws IOException {
        if (message == null || message.getOrderNo() == null) {
            log.warn("收到空的延迟关单消息，忽略处理");
            channel.basicAck(deliveryTag, false);
            return;
        }
        processOrderClose(message);
        localMessageService.markConsumed("ORDER_CLOSE", message.getOrderNo());
        channel.basicAck(deliveryTag, false);
    }

    private void processOrderClose(OrderCloseMessage message) {
        String orderNo = message.getOrderNo();
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
        if (orderInfo == null) {
            log.warn("延迟关单时订单不存在，orderNo={}", orderNo);
            return;
        }
        if (!OrderStatus.NOTPAY.getType().equals(orderInfo.getLegacyStatus())) {
            log.info("订单当前状态无需关单，orderNo={}, status={}", orderNo, orderInfo.getLegacyStatus());
            return;
        }
        String paymentType = orderInfo.getPaymentType();
        log.info("开始处理延迟关单，orderNo={}, paymentType={}", orderNo, paymentType);
        if (paymentOrderQueryApi.hasSuccessfulPayment(orderNo)) {
            log.info("订单存在有效支付成交，转为支付成功处理，orderNo={}", orderNo);
            orderInfoService.markPaid(orderNo);
            return;
        }
        channelOrderStatusDispatcher.checkOrderStatus(paymentType, orderNo);
    }
}
