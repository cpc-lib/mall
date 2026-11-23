package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.domain.mq.LocalMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox 按领域拆分后：锁键必须按应用名隔离，且只向扫描器传本域 biz_type，
 * 否则共用单键时每轮只有一个进程能扫描，其它域消息会饿死。
 */
class LocalMessageSendJobTest {

    @Test
    void relaysOnlyConfiguredBizTypesWithApplicationScopedLock() {
        LocalMessageService service = mock(LocalMessageService.class);
        DistributedLockTemplate lock = mock(DistributedLockTemplate.class);
        when(lock.tryExecute(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });

        LocalMessageSendJob job = new LocalMessageSendJob(
            service, lock, " REFUND_SYNC , ORDER_CLOSE ", "mall-order-service");
        job.publishPendingMessages();

        verify(lock).tryExecute(eq("scheduler:local-message-send:mall-order-service"), any(Runnable.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).publishPending(captor.capture());
        assertEquals(List.of("REFUND_SYNC", "ORDER_CLOSE"), captor.getValue());
    }

    @Test
    void skipsRoundWhenLockNotAcquired() {
        LocalMessageService service = mock(LocalMessageService.class);
        DistributedLockTemplate lock = mock(DistributedLockTemplate.class);
        when(lock.tryExecute(anyString(), any(Runnable.class))).thenReturn(false);

        new LocalMessageSendJob(service, lock, "ORDER_CLOSE", "mall-order-service").publishPendingMessages();

        verify(service, never()).publishPending(anyList());
    }

    @Test
    void emptyConfigFallsBackToAllTypes() {
        LocalMessageService service = mock(LocalMessageService.class);
        DistributedLockTemplate lock = mock(DistributedLockTemplate.class);
        when(lock.tryExecute(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });

        new LocalMessageSendJob(service, lock, "", "mall-user-bff").publishPendingMessages();

        verify(service).publishPending(List.of());
    }
}
