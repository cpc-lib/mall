package cc.ivera.shared.infrastructure.lock;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.FenceTokenHolder;
import cc.ivera.shared.domain.lock.LockAuditRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedissonDistributedLockTemplate Mockito 单测。
 * 覆盖：参数校验、tryLock 失败、nextFenceToken 异常时 finally 仍解锁（P0-3 修复）、
 * supplier 抛异常时 finally 解锁、成功路径返回值。
 */
class RedissonDistributedLockTemplateTest {

    private RedissonClient redisson;
    private RLock lock;
    private RScript script;
    private LockAuditRecorder recorder;
    private RedissonDistributedLockTemplate template;

    @BeforeEach
    void setUp() {
        while (FenceTokenHolder.get() != null) {
            FenceTokenHolder.release();
        }
        redisson = mock(RedissonClient.class);
        lock = mock(RLock.class);
        script = mock(RScript.class);
        recorder = mock(LockAuditRecorder.class);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(redisson.getScript(eq(StringCodec.INSTANCE))).thenReturn(script);
        template = new RedissonDistributedLockTemplate(redisson, recorder);
    }

    @AfterEach
    void tearDown() {
        while (FenceTokenHolder.get() != null) {
            FenceTokenHolder.release();
        }
    }

    private void stubLockSuccess() throws Exception {
        doReturn(true).when(lock).tryLock(anyLong(), eq(-1L), eq(TimeUnit.MILLISECONDS));
        when(lock.isHeldByThread(anyLong())).thenReturn(true);
    }

    /**
     * template 中 eval 调用：eval(Mode, script, ReturnType, keys, arg1, arg2) — 6 参数（后两个是 varargs）。
     * Mockito 对 varargs 重载匹配敏感，必须显式列出每个参数 matcher。
     */
    private static void stubEvalReturns(RScript scriptMock, Long value) throws Exception {
        doReturn(value).when(scriptMock).eval(
            any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class),
            anyList(), any(), any());
    }

    private static void stubEvalThrows(RScript scriptMock, Throwable t) throws Exception {
        doThrow(t).when(scriptMock).eval(
            any(RScript.Mode.class), anyString(), any(RScript.ReturnType.class),
            anyList(), any(), any());
    }

    @Test
    @DisplayName("参数校验：lockKey 为空抛 BizException")
    void rejectsBlankKey() throws Exception {
        assertThrows(BizException.class, () -> template.execute(null, 1000L, () -> "x"));
        assertThrows(BizException.class, () -> template.execute(" ", 1000L, () -> "x"));
        verify(lock, never()).tryLock(anyLong(), eq(-1L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("参数校验：supplier 为空抛 BizException")
    void rejectsNullSupplier() {
        assertThrows(BizException.class, () -> template.execute("k", 1000L, (Supplier<Object>) null));
    }

    @Test
    @DisplayName("tryLock 失败 → 抛 BizException，不调用 supplier")
    void lockFailedRejectsSupplier() throws Exception {
        doReturn(false).when(lock).tryLock(anyLong(), eq(-1L), eq(TimeUnit.MILLISECONDS));

        assertThrows(BizException.class, () -> template.execute("ORDER:1", 1000L, () -> "done"));

        verify(recorder).record(eq("ACQUIRE_FAILED"), eq("ORDER:1"), eq(null), eq(0L));
    }

    @Test
    @DisplayName("成功路径：supplier 返回值正确传递，锁最终被释放")
    void successPathReturnsResultAndUnlocks() throws Exception {
        stubLockSuccess();
        stubEvalReturns(script, 123L);

        String result = template.execute("ORDER:1", 1000L, () -> "done");

        assertEquals("done", result);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("P0-3 修复：nextFenceToken 抛异常时 finally 仍解锁，防止 Watchdog 永久泄漏")
    void fenceTokenFailureStillUnlocks() throws Exception {
        stubLockSuccess();
        stubEvalThrows(script, new RuntimeException("Redis 抖动"));

        assertThrows(RuntimeException.class, () -> template.execute("ORDER:1", 1000L, () -> "should-not-run"));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("supplier 抛异常时 finally 仍解锁")
    void supplierFailureStillUnlocks() throws Exception {
        stubLockSuccess();
        stubEvalReturns(script, 42L);

        assertThrows(RuntimeException.class, () -> template.execute("ORDER:1", 1000L, () -> {
            throw new RuntimeException("业务异常");
        }));

        verify(lock).unlock();
    }
}
