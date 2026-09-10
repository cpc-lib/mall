package cc.ivera.order.interfaces.mq;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelOrderStatusDispatcher;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.OrderCloseMessage;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 延迟关单消费者特征测试：锁定 MANUAL ACK 语义。
 * 业务异常必须继续向外抛出且不得 ACK；只有业务与 CONSUMED 回写完整成功后才 ACK。
 */
class OrderCloseConsumerTest {

    private OrderInfoService orderInfoService;
    private ChannelOrderStatusDispatcher channelOrderStatusDispatcher;
    private LocalMessageService localMessageService;
    private Channel channel;
    private OrderCloseConsumer consumer;

    @BeforeEach
    void setUp() {
        orderInfoService = mock(OrderInfoService.class);
        channelOrderStatusDispatcher = mock(ChannelOrderStatusDispatcher.class);
        localMessageService = mock(LocalMessageService.class);
        channel = mock(Channel.class);
        consumer = new OrderCloseConsumer(orderInfoService, channelOrderStatusDispatcher, localMessageService);
    }

    @Test
    void handleOrderClose_businessFailure_propagatesAndDoesNotMarkConsumed() throws Exception {
        OrderInfo orderInfo = notPayOrder("ORD-FAIL");
        when(orderInfoService.getOrderByOrderNo("ORD-FAIL")).thenReturn(orderInfo);
        doThrow(new RuntimeException("channel unavailable"))
            .when(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-FAIL");

        OrderCloseMessage message = new OrderCloseMessage();
        message.setOrderNo("ORD-FAIL");

        assertThrows(RuntimeException.class, () -> consumer.handleOrderClose(message, channel, 101L));

        verify(localMessageService, never()).markConsumed(anyString(), anyString());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void handleOrderClose_success_marksConsumedAfterBusinessProcessing() throws Exception {
        OrderInfo orderInfo = notPayOrder("ORD-OK");
        when(orderInfoService.getOrderByOrderNo("ORD-OK")).thenReturn(orderInfo);

        OrderCloseMessage message = new OrderCloseMessage();
        message.setOrderNo("ORD-OK");

        consumer.handleOrderClose(message, channel, 102L);

        InOrder inOrder = inOrder(channelOrderStatusDispatcher, localMessageService, channel);
        inOrder.verify(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-OK");
        inOrder.verify(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, "ORD-OK");
        inOrder.verify(channel).basicAck(102L, false);
    }

    @Test
    void handleOrderClose_ackFailure_propagatesAfterMarkConsumed() throws Exception {
        OrderInfo orderInfo = notPayOrder("ORD-ACK-FAIL");
        when(orderInfoService.getOrderByOrderNo("ORD-ACK-FAIL")).thenReturn(orderInfo);
        doThrow(new java.io.IOException("ack failed"))
            .when(channel).basicAck(105L, false);

        OrderCloseMessage message = new OrderCloseMessage();
        message.setOrderNo("ORD-ACK-FAIL");

        assertThrows(java.io.IOException.class,
            () -> consumer.handleOrderClose(message, channel, 105L));

        verify(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-ACK-FAIL");
        verify(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, "ORD-ACK-FAIL");
        verify(channel).basicAck(105L, false);
    }

    @Test
    void handleOrderClose_markConsumedFailure_doesNotAck() throws Exception {
        OrderInfo orderInfo = notPayOrder("ORD-CONSUMED-FAIL");
        when(orderInfoService.getOrderByOrderNo("ORD-CONSUMED-FAIL")).thenReturn(orderInfo);
        doThrow(new RuntimeException("outbox update failed"))
            .when(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, "ORD-CONSUMED-FAIL");

        OrderCloseMessage message = new OrderCloseMessage();
        message.setOrderNo("ORD-CONSUMED-FAIL");

        assertThrows(RuntimeException.class, () -> consumer.handleOrderClose(message, channel, 103L));

        verify(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-CONSUMED-FAIL");
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void handleOrderClose_emptyMessage_acknowledgesAndIgnores() throws Exception {
        consumer.handleOrderClose(null, channel, 104L);

        verifyNoInteractions(orderInfoService, channelOrderStatusDispatcher, localMessageService);
        verify(channel).basicAck(104L, false);
    }

    private OrderInfo notPayOrder(String orderNo) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(orderNo);
        orderInfo.setLegacyStatus(OrderStatus.NOTPAY.getType());
        orderInfo.setPaymentType("微信");
        return orderInfo;
    }
}
