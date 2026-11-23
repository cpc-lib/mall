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
            // 订单已离开 NOTPAY：幂等兜底库存操作，防止 CAS 成功但 afterCommit 内远程调用超时导致泄漏。
            if (OrderStatus.CLOSED.getType().equals(orderInfo.getLegacyStatus())
                || OrderStatus.CANCEL.getType().equals(orderInfo.getLegacyStatus())) {
                orderInfoService.ensureReservationReleased(orderNo);
            } else if (OrderStatus.SUCCESS.getType().equals(orderInfo.getLegacyStatus())) {
                orderInfoService.ensureReservationCommitted(orderNo);
            }
            log.info("订单当前状态无需关单，orderNo={}, status={}", orderNo, orderInfo.getLegacyStatus());
            return;
        }
        String paymentType = orderInfo.getPaymentType();
        log.info("开始处理延迟关单，orderNo={}, paymentType={}", orderNo, paymentType);
        if (paymentOrderQueryApi.hasSuccessfulPayment(orderNo)) {
            log.info("订单存在有效支付成交，转为支付成功处理，orderNo={}", orderNo);
            orderInfoService.markPaid(orderNo);
            // markPaid 内部 CAS 成功后在 afterCommit 里调 commitReservation；
            // 如果 afterCommit 内远程调用超时，这里幂等再调一次兜底。
            orderInfoService.ensureReservationCommitted(orderNo);
            return;
        }
        channelOrderStatusDispatcher.checkOrderStatus(paymentType, orderNo);
    }
}
