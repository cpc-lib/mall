package cc.ivera.order.interfaces.mq;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.application.OrderPaymentResult;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.shared.domain.mq.ConsumerDedupeService;
import cc.ivera.shared.domain.mq.LatePaymentMessage;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.PaymentSucceededMessage;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
public class PaymentSucceededConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentSucceededConsumer.class);
    static final String CONSUMER = "order.payment-succeeded";
    private final OrderInfoService orderInfoService;
    private final ConsumerDedupeService consumerDedupeService;
    private final LocalMessageService localMessageService;

    public PaymentSucceededConsumer(OrderInfoService orderInfoService,
                                    ConsumerDedupeService consumerDedupeService,
                                    LocalMessageService localMessageService) {
        this.orderInfoService = orderInfoService;
        this.consumerDedupeService = consumerDedupeService;
        this.localMessageService = localMessageService;
    }

    @RabbitListener(queues = "payment.succeeded.order.queue")
    public void handle(PaymentSucceededMessage message, Channel channel,
                       @Header("amqp_deliveryTag") long deliveryTag) throws IOException {
        if (message == null || !StringUtils.hasText(message.getOrderNo()) || !StringUtils.hasText(message.getPaymentNo())) {
            log.warn("收到无效的支付成交消息，忽略处理，message={}", message);
            channel.basicAck(deliveryTag, false);
            return;
        }
        String eventId = StringUtils.hasText(message.getEventId()) ? message.getEventId() : message.getPaymentNo();
        if (consumerDedupeService.isConsumed(CONSUMER, eventId)) {
            log.info("支付成交事件重复投递，忽略，eventId={}", eventId);
            channel.basicAck(deliveryTag, false);
            return;
        }
        process(message);
        consumerDedupeService.markConsumed(CONSUMER, eventId);
        channel.basicAck(deliveryTag, false);
    }

    private void process(PaymentSucceededMessage message) {
        String orderNo = message.getOrderNo();
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            log.error("支付成交事件对应订单不存在，按无效消息确认，orderNo={}, paymentNo={}", orderNo, message.getPaymentNo());
            return;
        }
        if (OrderStatus.SUCCESS.getType().equals(order.getLegacyStatus())) {
            // 幂等兜底：即使订单已是 SUCCESS，仍幂等提交一次库存预占。
            // 防止上次 CAS 成功但 afterCommit 内远程 commitReservation 超时，导致 locked_stock 永久泄漏。
            orderInfoService.ensureReservationCommitted(orderNo);
            log.info("订单已支付，支付成交事件幂等忽略，orderNo={}, paymentNo={}", orderNo, message.getPaymentNo());
            return;
        }
        if (OrderStatus.NOTPAY.getType().equals(order.getLegacyStatus())) {
            OrderPaymentResult result = orderInfoService.markPaid(orderNo);
            if (result == OrderPaymentResult.PAID || result == OrderPaymentResult.ALREADY_PAID) {
                log.info("支付成交推进订单成功，orderNo={}, paymentNo={}, result={}", orderNo, message.getPaymentNo(), result);
                return;
            }
            if (result == OrderPaymentResult.NOT_FOUND) {
                log.error("支付成交推进时订单不存在，orderNo={}, paymentNo={}", orderNo, message.getPaymentNo());
                return;
            }
            log.warn("支付成交推进时订单已关闭，转为晚到支付，orderNo={}, paymentNo={}", orderNo, message.getPaymentNo());
            publishLatePayment(message);
            return;
        }
        log.warn("订单状态不允许推进为已支付，转为晚到支付，orderNo={}, status={}, paymentNo={}",
            orderNo, order.getLegacyStatus(), message.getPaymentNo());
        publishLatePayment(message);
    }

    private void publishLatePayment(PaymentSucceededMessage message) {
        LatePaymentMessage late = new LatePaymentMessage();
        late.setEventId(message.getPaymentNo());
        late.setOrderNo(message.getOrderNo());
        late.setPaymentNo(message.getPaymentNo());
        late.setChannel(message.getChannel());
        late.setPaidAmount(message.getPaidAmount());
        late.setPaidTime(message.getPaidTime());
        localMessageService.saveAndPublishAfterCommit("PAYMENT_LATE_ARRIVED", message.getPaymentNo(), late);
    }
}
