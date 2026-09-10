package cc.ivera.refund.interfaces.job;

import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * 退款状态同步 DB 最终一致性兜底测试。
 * 锁定 Scheduler 的核心契约：扫描 APPROVED + PROCESSING 退款并复用既有 queryRefundStatus 链路，
 * 且单笔失败不能阻塞同批次后续退款继续收敛。
 */
class RefundStatusSyncSchedulerTest {

    private RefundInfoRepository refundInfoRepository;
    private RefundApplicationService refundApplicationService;
    private RefundStatusSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        refundInfoRepository = mock(RefundInfoRepository.class);
        refundApplicationService = mock(RefundApplicationService.class);
        scheduler = new RefundStatusSyncScheduler(refundInfoRepository, refundApplicationService);
    }

    @Test
    void scan_syncsAllProcessingRefundsEvenWhenOneFails() {
        RefundInfo first = refund("RFD-FAIL");
        RefundInfo second = refund("RFD-OK");
        when(refundInfoRepository.listProcessingApproved())
            .thenReturn(Arrays.asList(first, second));
        doThrow(new RuntimeException("channel unavailable"))
            .when(refundApplicationService).queryRefundStatus("RFD-FAIL");

        scheduler.scan();

        verify(refundInfoRepository).listProcessingApproved();
        verify(refundApplicationService).queryRefundStatus("RFD-FAIL");
        verify(refundApplicationService).queryRefundStatus("RFD-OK");
    }

    @Test
    void scan_noProcessingRefunds_doesNotCallChannel() {
        when(refundInfoRepository.listProcessingApproved())
            .thenReturn(Collections.emptyList());

        scheduler.scan();

        verifyNoInteractions(refundApplicationService);
    }

    private RefundInfo refund(String refundNo) {
        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setRefundNo(refundNo);
        return refundInfo;
    }
}
