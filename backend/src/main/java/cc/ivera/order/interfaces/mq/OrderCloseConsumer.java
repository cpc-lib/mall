package cc.ivera.order.interfaces.mq;


import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.infrastructure.mq.OrderCloseRabbitConfig;
import cc.ivera.payment.application.ChannelOrderStatusDispatcher;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.OrderCloseMessage;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class OrderCloseConsumer {

    private final OrderInfoService orderInfoService;

    private final ChannelOrderStatusDispatcher channelOrderStatusDispatcher;

    private final LocalMessageService localMessageService;

    public OrderCloseConsumer(
        OrderInfoService orderInfoService,
        ChannelOrderStatusDispatcher channelOrderStatusDispatcher,
        LocalMessageService localMessageService
    ) {
        this.orderInfoService = orderInfoService;
        this.channelOrderStatusDispatcher = channelOrderStatusDispatcher;
        this.localMessageService = localMessageService;
    }

    @RabbitListener(queues = OrderCloseRabbitConfig.ORDER_CLOSE_RELEASE_QUEUE)
    public void handleOrderClose(OrderCloseMessage message,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message == null || message.getOrderNo() == null) {
            log.warn("收到空的延迟关单消息，忽略处理");
            channel.basicAck(deliveryTag, false);
            return;
        }

        processOrderClose(message);
        // MANUAL ACK：业务处理与本地消息 CONSUMED 回写全部成功后才确认 broker 消息。
        // 任一步骤异常均继续向外抛给 Spring Retry；重试耗尽后由容器 reject，进入 Failure DLQ。
        localMessageService.markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, message.getOrderNo());
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

        channelOrderStatusDispatcher.checkOrderStatus(paymentType, orderNo);
    }
}
