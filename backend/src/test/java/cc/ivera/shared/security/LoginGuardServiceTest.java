package cc.ivera.shared.security;

import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * LoginGuardService 特征测试：mock StringRedisTemplate，不连真实 Redis。
 * 锁定规则：失败计数与锁定仅按用户名维度，任何情况下都不得读写 IP 维度的 key。
 */
class LoginGuardServiceTest {

    private static final String FAIL_KEY = "auth:login_fail:alice";
    private static final String LOCK_KEY = "auth:login_lock:alice";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private LoginGuardService guard;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        guard = new LoginGuardService(redisTemplate);
    }

    @Test
    void checkLocked_未锁定_放行() {
        when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(false);

        assertDoesNotThrow(() -> guard.checkLocked("alice"));
    }

    @Test
    void checkLocked_用户名已锁定_抛出带剩余分钟的账号锁定提示() {
        when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(true);
        when(redisTemplate.getExpire(eq(LOCK_KEY), eq(TimeUnit.SECONDS))).thenReturn(300L);

        BizException ex = assertThrows(BizException.class, () -> guard.checkLocked("alice"));
        assertTrue(ex.getMessage().contains("账号已锁定"), ex.getMessage());
        assertTrue(ex.getMessage().contains("5分钟后再试"), ex.getMessage());
    }

    @Test
    void checkLocked_永不检查IP锁定key() {
        guard.checkLocked("alice");

        verify(redisTemplate, never()).hasKey(contains("login_lock_ip"));
    }

    @Test
    void recordFailure_按用户名计数并刷新滑动窗口() {
        when(valueOps.increment(FAIL_KEY)).thenReturn(1L);

        guard.recordFailure("alice");

        verify(valueOps).increment(FAIL_KEY);
        verify(redisTemplate).expire(FAIL_KEY, 600L, TimeUnit.SECONDS);
        // 未达阈值不置锁
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void recordFailure_连续3次失败_置用户名锁并清除计数器() {
        when(valueOps.increment(FAIL_KEY)).thenReturn(1L, 2L, 3L);

        guard.recordFailure("alice");
        guard.recordFailure("alice");
        guard.recordFailure("alice");

        verify(valueOps).set(eq(LOCK_KEY), eq("1"), eq(600L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete(FAIL_KEY);
    }

    @Test
    void recordFailure_任何情况下都不触碰IP维度key() {
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L);

        guard.recordFailure("alice");
        guard.recordFailure("alice");
        guard.recordFailure("alice");

        verify(valueOps, never()).increment(contains("login_fail_ip"));
        verify(valueOps, never()).set(contains("login_lock_ip"), anyString(), anyLong(), any(TimeUnit.class));
        verify(redisTemplate, never()).expire(contains("login_fail_ip"), anyLong(), any(TimeUnit.class));
        verify(redisTemplate, never()).delete(contains("login_fail_ip"));
        verify(redisTemplate, never()).delete(contains("login_lock_ip"));
    }

    @Test
    void recordFailure_用户名为空_不触碰Redis() {
        guard.recordFailure(null);
        guard.recordFailure("   ");

        verifyNoInteractions(valueOps);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void clear_仅清除用户名维度key() {
        guard.clear("alice");

        verify(redisTemplate).delete(FAIL_KEY);
        verify(redisTemplate).delete(LOCK_KEY);
        verify(redisTemplate, never()).delete(contains("login_fail_ip"));
        verify(redisTemplate, never()).delete(contains("login_lock_ip"));
    }

    @Test
    void clearUsername_管理员重置密码_清除用户名锁定() {
        guard.clearUsername("alice");

        verify(redisTemplate).delete(FAIL_KEY);
        verify(redisTemplate).delete(LOCK_KEY);
    }

    @Test
    void redis异常_checkLocked_failOpen不阻断() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        assertDoesNotThrow(() -> guard.checkLocked("alice"));
    }

    @Test
    void redis异常_recordFailure_failOpen不抛错() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        assertDoesNotThrow(() -> guard.recordFailure("alice"));
    }

    @Test
    void 锁定提示_ttl缺失时按完整10分钟展示() {
        when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(true);
        when(redisTemplate.getExpire(eq(LOCK_KEY), eq(TimeUnit.SECONDS))).thenReturn(-1L);

        BizException ex = assertThrows(BizException.class, () -> guard.checkLocked("alice"));
        assertEquals("密码错误次数过多，账号已锁定，请约10分钟后再试", ex.getMessage());
    }
}
