package cc.ivera.refund.interfaces.mq;

import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.RefundStatusSyncMessage;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 退款状态同步消费者特征测试：锁定 MANUAL ACK 语义。
 * 业务异常必须继续向外抛出且不得 ACK；只有业务与 CONSUMED 回写完整成功后才 ACK。
 */
class RefundStatusSyncConsumerTest {

    private RefundApplicationService refundApplicationService;
    private LocalMessageService localMessageService;
    private Channel channel;
    private RefundStatusSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        refundApplicationService = mock(RefundApplicationService.class);
        localMessageService = mock(LocalMessageService.class);
        channel = mock(Channel.class);
        consumer = new RefundStatusSyncConsumer(refundApplicationService, localMessageService);
    }

    @Test
    void handleRefundStatusSync_businessFailure_propagatesAndDoesNotMarkConsumed() throws Exception {
        doThrow(new RuntimeException("channel unavailable"))
            .when(refundApplicationService).queryRefundStatus("RFD-FAIL");

        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo("RFD-FAIL");

        assertThrows(RuntimeException.class, () -> consumer.handleRefundStatusSync(message, channel, 201L));

        verify(localMessageService, never()).markConsumed(anyString(), anyString());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void handleRefundStatusSync_success_marksConsumedAfterBusinessProcessing() throws Exception {
        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo("RFD-OK");

        consumer.handleRefundStatusSync(message, channel, 202L);

        InOrder inOrder = inOrder(refundApplicationService, localMessageService, channel);
        inOrder.verify(refundApplicationService).queryRefundStatus("RFD-OK");
        inOrder.verify(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_REFUND_SYNC, "RFD-OK");
        inOrder.verify(channel).basicAck(202L, false);
    }

    @Test
    void handleRefundStatusSync_ackFailure_propagatesAfterMarkConsumed() throws Exception {
        doThrow(new java.io.IOException("ack failed"))
            .when(channel).basicAck(205L, false);

        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo("RFD-ACK-FAIL");

        assertThrows(java.io.IOException.class,
            () -> consumer.handleRefundStatusSync(message, channel, 205L));

        verify(refundApplicationService).queryRefundStatus("RFD-ACK-FAIL");
        verify(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_REFUND_SYNC, "RFD-ACK-FAIL");
        verify(channel).basicAck(205L, false);
    }

    @Test
    void handleRefundStatusSync_markConsumedFailure_doesNotAck() throws Exception {
        doThrow(new RuntimeException("outbox update failed"))
            .when(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_REFUND_SYNC, "RFD-CONSUMED-FAIL");

        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo("RFD-CONSUMED-FAIL");

        assertThrows(RuntimeException.class, () -> consumer.handleRefundStatusSync(message, channel, 203L));

        verify(refundApplicationService).queryRefundStatus("RFD-CONSUMED-FAIL");
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void handleRefundStatusSync_emptyMessage_acknowledgesAndIgnores() throws Exception {
        consumer.handleRefundStatusSync(null, channel, 204L);

        verifyNoInteractions(refundApplicationService, localMessageService);
        verify(channel).basicAck(204L, false);
    }
}
