package cc.ivera.order.interfaces.mq;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelOrderStatusDispatcher;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.OrderCloseMessage;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 延迟关单消费者特征测试：锁定 AUTO ACK 所依赖的监听器语义。
 * 业务异常必须继续向外抛出，只有完整成功后才回写本地消息为 CONSUMED。
 */
class OrderCloseConsumerTest {

    private OrderInfoService orderInfoService;
    private ChannelOrderStatusDispatcher channelOrderStatusDispatcher;
    private LocalMessageService localMessageService;
    private OrderCloseConsumer consumer;

    @BeforeEach
    void setUp() {
        orderInfoService = mock(OrderInfoService.class);
        channelOrderStatusDispatcher = mock(ChannelOrderStatusDispatcher.class);
        localMessageService = mock(LocalMessageService.class);
        consumer = new OrderCloseConsumer(orderInfoService, channelOrderStatusDispatcher, localMessageService);
    }

    @Test
    void handleOrderClose_businessFailure_propagatesAndDoesNotMarkConsumed() {
        OrderInfo orderInfo = notPayOrder("ORD-FAIL");
        when(orderInfoService.getOrderByOrderNo("ORD-FAIL")).thenReturn(orderInfo);
        doThrow(new RuntimeException("channel unavailable"))
            .when(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-FAIL");

        OrderCloseMessage message = new OrderCloseMessage();
        message.setOrderNo("ORD-FAIL");

        assertThrows(RuntimeException.class, () -> consumer.handleOrderClose(message));

        verify(localMessageService, never()).markConsumed(anyString(), anyString());
    }

    @Test
    void handleOrderClose_success_marksConsumedAfterBusinessProcessing() {
        OrderInfo orderInfo = notPayOrder("ORD-OK");
        when(orderInfoService.getOrderByOrderNo("ORD-OK")).thenReturn(orderInfo);

        OrderCloseMessage message = new OrderCloseMessage();
        message.setOrderNo("ORD-OK");

        consumer.handleOrderClose(message);

        verify(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-OK");
        verify(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, "ORD-OK");
    }

    private OrderInfo notPayOrder(String orderNo) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(orderNo);
        orderInfo.setLegacyStatus(OrderStatus.NOTPAY.getType());
        orderInfo.setPaymentType("微信");
        return orderInfo;
    }
}
