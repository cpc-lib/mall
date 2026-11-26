package cc.ivera.order.interfaces.mq;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.shared.domain.mq.ConsumerDedupeService;
import cc.ivera.shared.domain.mq.LatePaymentMessage;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.PaymentSucceededMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSucceededConsumerTest {

    private final OrderInfoService orderInfoService = mock(OrderInfoService.class);
    private final ConsumerDedupeService consumerDedupeService = mock(ConsumerDedupeService.class);
    private final LocalMessageService localMessageService = mock(LocalMessageService.class);
    private final Channel channel = mock(Channel.class);
    private final PaymentSucceededConsumer consumer = new PaymentSucceededConsumer(
        orderInfoService, consumerDedupeService, localMessageService);

    @Test
    void closedOrderPublishesLatePaymentBeforeDedupeAndAck() throws Exception {
        PaymentSucceededMessage message = new PaymentSucceededMessage();
        message.setEventId("PAY-1");
        message.setOrderNo("ORD-1");
        message.setPaymentNo("PAY-1");
        message.setChannel("WXPAY");
        message.setPaidAmount(100);
        message.setPaidTime(new Date(1_000L));
        OrderInfo closed = new OrderInfo();
        closed.setOrderNo("ORD-1");
        closed.setLegacyStatus(OrderStatus.CLOSED.getType());
        when(orderInfoService.getOrderByOrderNo("ORD-1")).thenReturn(closed);

        consumer.handle(message, channel, 11L);

        ArgumentCaptor<LatePaymentMessage> captor = ArgumentCaptor.forClass(LatePaymentMessage.class);
        verify(localMessageService).saveAndPublishAfterCommit(
            eq("PAYMENT_LATE_ARRIVED"), eq("PAY-1"), captor.capture());
        assertEquals("ORD-1", captor.getValue().getOrderNo());
        assertEquals("PAY-1", captor.getValue().getPaymentNo());
        assertEquals(100, captor.getValue().getPaidAmount());

        InOrder ordered = inOrder(localMessageService, consumerDedupeService, channel);
        ordered.verify(localMessageService).saveAndPublishAfterCommit(
            eq("PAYMENT_LATE_ARRIVED"), eq("PAY-1"), eq(captor.getValue()));
        ordered.verify(consumerDedupeService).markConsumed("order.payment-succeeded", "PAY-1");
        ordered.verify(channel).basicAck(11L, false);
    }
}
