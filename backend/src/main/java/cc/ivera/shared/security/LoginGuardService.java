package cc.ivera.shared.security;

import cc.ivera.shared.domain.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录防爆破守卫：用户名 + 来源 IP 双维度失败计数与锁定。
 * 计数采用滑动窗口（每次失败刷新 TTL）；任一维度累计失败达到阈值即置锁，
 * 锁定期间即使密码正确也拒绝登录。Redis 异常时 fail-open，不阻断正常登录。
 */
@Service
public class LoginGuardService {
    public static final String USERNAME_FAIL_PREFIX = "auth:login_fail:";
    public static final String USERNAME_LOCK_PREFIX = "auth:login_lock:";
    public static final String IP_FAIL_PREFIX = "auth:login_fail_ip:";
    public static final String IP_LOCK_PREFIX = "auth:login_lock_ip:";

    static final int MAX_ATTEMPTS = 3;
    static final long LOCK_SECONDS = 10 * 60L;

    private final StringRedisTemplate redisTemplate;

    public LoginGuardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 登录开始时调用：任一维度已锁定即抛出（先于查库，锁定登录不触达数据库）
     */
    public void checkLocked(String username, String ip) {
        throwIfLocked(USERNAME_LOCK_PREFIX + safe(username), "密码错误次数过多，账号已锁定，请约");
        throwIfLocked(IP_LOCK_PREFIX + safe(ip), "当前网络环境已被临时锁定，请约");
    }

    /**
     * 密码错误时调用：双维度各自计数，达到阈值置锁并清除计数器
     */
    public void recordFailure(String username, String ip) {
        recordOne(USERNAME_FAIL_PREFIX, USERNAME_LOCK_PREFIX, username);
        recordOne(IP_FAIL_PREFIX, IP_LOCK_PREFIX, ip);
    }

    /**
     * 登录成功时调用：清除双维度失败计数
     */
    public void clear(String username, String ip) {
        clearUsername(username);
        clearIp(ip);
    }

    /**
     * 清除指定用户名的失败计数与锁定（管理员重置密码后调用）
     */
    public void clearUsername(String username) {
        try {
            String name = safe(username);
            if (name.isEmpty()) return;
            redisTemplate.delete(USERNAME_FAIL_PREFIX + name);
            redisTemplate.delete(USERNAME_LOCK_PREFIX + name);
        } catch (Exception ignored) {
        }
    }

    /**
     * 清除指定 IP 的失败计数与锁定
     */
    public void clearIp(String ip) {
        try {
            String addr = safe(ip);
            if (addr.isEmpty()) return;
            redisTemplate.delete(IP_FAIL_PREFIX + addr);
            redisTemplate.delete(IP_LOCK_PREFIX + addr);
        } catch (Exception ignored) {
        }
    }

    private void throwIfLocked(String lockKey, String messagePrefix) {
        try {
            if (!redisTemplate.hasKey(lockKey)) return;
            Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            long minutes = (ttl == null || ttl <= 0) ? LOCK_SECONDS / 60 : Math.max(1, (ttl + 59) / 60);
            throw new BizException(messagePrefix + minutes + "分钟后再试");
        } catch (BizException e) {
            throw e;
        } catch (Exception ignored) { /* fail-open */ }
    }

    private void recordOne(String failPrefix, String lockPrefix, String identity) {
        String id = safe(identity);
        if (id.isEmpty()) return;
        try {
            String failKey = failPrefix + id;
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count == null) return;
            redisTemplate.expire(failKey, LOCK_SECONDS, TimeUnit.SECONDS);
            if (count >= MAX_ATTEMPTS) {
                redisTemplate.opsForValue().set(lockPrefix + id, "1", LOCK_SECONDS, TimeUnit.SECONDS);
                redisTemplate.delete(failKey);
            }
        } catch (Exception ignored) { /* fail-open */ }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
