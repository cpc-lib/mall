package cc.ivera.mq;

import cc.ivera.config.OrderCloseRabbitConfig;
import cc.ivera.entity.OrderInfo;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayType;
import cc.ivera.service.AliPayService;
import cc.ivera.service.OrderInfoService;
import cc.ivera.service.wxpay.WxPayOrderFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderCloseConsumer {

    private final OrderInfoService orderInfoService;

    private final WxPayOrderFacade wxPayOrderFacade;

    private final AliPayService aliPayService;

    public OrderCloseConsumer(
        OrderInfoService orderInfoService,
        WxPayOrderFacade wxPayOrderFacade,
        AliPayService aliPayService
    ) {
        this.orderInfoService = orderInfoService;
        this.wxPayOrderFacade = wxPayOrderFacade;
        this.aliPayService = aliPayService;
    }

    @RabbitListener(queues = OrderCloseRabbitConfig.ORDER_CLOSE_RELEASE_QUEUE)
    public void handleOrderClose(OrderCloseMessage message) {
        if (message == null || message.getOrderNo() == null) {
            log.warn("收到空的延迟关单消息，忽略处理");
            return;
        }

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

        if (PayType.WXPAY.getType().equals(paymentType)) {
            wxPayOrderFacade.checkOrderStatus(orderNo);
            return;
        }
        if (PayType.ALIPAY.getType().equals(paymentType)) {
            aliPayService.checkOrderStatus(orderNo);
            return;
        }

        log.warn("未知支付类型，无法处理延迟关单，orderNo={}, paymentType={}", orderNo, paymentType);
    }
}
