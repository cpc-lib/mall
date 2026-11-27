package cc.ivera.order.application.impl;

import cc.ivera.order.domain.repository.SagaHistoryRepository;
import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SagaHistoryCleanupServiceImpl 纯单测：不启动 Spring、不依赖 DM8，
 * 仅验证单批清理的调用顺序、空短路与终态数量校验回滚。
 */
@ExtendWith(MockitoExtension.class)
class SagaHistoryCleanupServiceImplTest {

    private static final int BATCH_SIZE = 500;

    @Mock
    private SagaHistoryRepository sagaHistoryRepository;

    @InjectMocks
    private SagaHistoryCleanupServiceImpl cleanupService;

    private final Date cutoff = new Date(0L);

    @Test
    void returnsZeroAndSkipsDeletesWhenNoCleanableInstances() {
        when(sagaHistoryRepository.findCleanableInstanceIds(cutoff, BATCH_SIZE)).thenReturn(List.of());

        int result = cleanupService.cleanBatch(cutoff);

        assertEquals(0, result);
        verify(sagaHistoryRepository, never()).deleteStateInstancesByMachineInstIds(anyList());
        verify(sagaHistoryRepository, never()).deleteMachineInstancesTerminalBefore(any(), anyList());
    }

    @Test
    void deletesStatesBeforeMachinesAndReturnsDeletedCount() {
        List<String> ids = List.of("inst-1", "inst-2");
        when(sagaHistoryRepository.findCleanableInstanceIds(cutoff, BATCH_SIZE)).thenReturn(ids);
        when(sagaHistoryRepository.deleteStateInstancesByMachineInstIds(ids)).thenReturn(2);
        when(sagaHistoryRepository.deleteMachineInstancesTerminalBefore(cutoff, ids)).thenReturn(2);

        int result = cleanupService.cleanBatch(cutoff);

        assertEquals(2, result);
        InOrder order = inOrder(sagaHistoryRepository);
        order.verify(sagaHistoryRepository).deleteStateInstancesByMachineInstIds(ids);
        order.verify(sagaHistoryRepository).deleteMachineInstancesTerminalBefore(cutoff, ids);
    }

    @Test
    void throwsBizExceptionWhenTerminalDeleteCountMismatch() {
        List<String> ids = List.of("inst-1", "inst-2");
        when(sagaHistoryRepository.findCleanableInstanceIds(cutoff, BATCH_SIZE)).thenReturn(ids);
        when(sagaHistoryRepository.deleteStateInstancesByMachineInstIds(ids)).thenReturn(2);
        // 终态 CAS 只删到 1 条：查询后实例状态被推进，应抛异常触发事务回滚
        when(sagaHistoryRepository.deleteMachineInstancesTerminalBefore(cutoff, ids)).thenReturn(1);

        assertThrows(BizException.class, () -> cleanupService.cleanBatch(cutoff));
    }
}
