package cc.ivera.refund.interfaces.mq;

import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.domain.mq.RefundStatusSyncMessage;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 退款状态同步消费者特征测试：锁定 AUTO ACK 所依赖的监听器语义。
 * 业务异常必须继续向外抛出，只有完整成功后才回写本地消息为 CONSUMED。
 */
class RefundStatusSyncConsumerTest {

    private RefundApplicationService refundApplicationService;
    private LocalMessageService localMessageService;
    private RefundStatusSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        refundApplicationService = mock(RefundApplicationService.class);
        localMessageService = mock(LocalMessageService.class);
        consumer = new RefundStatusSyncConsumer(refundApplicationService, localMessageService);
    }

    @Test
    void handleRefundStatusSync_businessFailure_propagatesAndDoesNotMarkConsumed() {
        doThrow(new RuntimeException("channel unavailable"))
            .when(refundApplicationService).queryRefundStatus("RFD-FAIL");

        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo("RFD-FAIL");

        assertThrows(RuntimeException.class, () -> consumer.handleRefundStatusSync(message));

        verify(localMessageService, never()).markConsumed(anyString(), anyString());
    }

    @Test
    void handleRefundStatusSync_success_marksConsumedAfterBusinessProcessing() {
        RefundStatusSyncMessage message = new RefundStatusSyncMessage();
        message.setRefundNo("RFD-OK");

        consumer.handleRefundStatusSync(message);

        verify(refundApplicationService).queryRefundStatus("RFD-OK");
        verify(localMessageService).markConsumed(LocalMessage.BIZ_TYPE_REFUND_SYNC, "RFD-OK");
    }
}
