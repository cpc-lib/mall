package cc.ivera.shared.infrastructure.lock;


import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.domain.lock.FenceTokenHolder;
import cc.ivera.shared.domain.lock.LockAuditRecorder;
import cc.ivera.shared.domain.lock.LockContext;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@ConditionalOnClass(RedissonClient.class)
@Slf4j
public class RedissonDistributedLockTemplate implements DistributedLockTemplate {

    private static final String FENCE_KEY = "lock:fence:seq";
    private static final String FENCE_SEED_SCRIPT =
        "redis.call('set', KEYS[1], ARGV[1], 'nx') "
            + "redis.call('expire', KEYS[1], ARGV[2]) "
            + "return redis.call('incr', KEYS[1])";
    private static final long FENCE_TTL_SECONDS = TimeUnit.DAYS.toSeconds(30L);
    private static final long DEFAULT_HOLD_WARN_MILLIS = 5000L;
    private static final String EVENT_ACQUIRED = "ACQUIRED";
    private static final String EVENT_RELEASED = "RELEASED";
    private static final String EVENT_ACQUIRE_FAILED = "ACQUIRE_FAILED";

    private final RedissonClient redissonClient;

    private final LockAuditRecorder lockAuditRecorder;

    public RedissonDistributedLockTemplate(RedissonClient redissonClient, LockAuditRecorder lockAuditRecorder) {
        this.redissonClient = redissonClient;
        this.lockAuditRecorder = lockAuditRecorder;
    }

    @Override
    public <T> T execute(String lockKey, long waitTimeMillis, Supplier<T> supplier) {
        return execute(lockKey, waitTimeMillis, DEFAULT_HOLD_WARN_MILLIS, supplier);
    }

    @Override
    public <T> T execute(String lockKey, long waitTimeMillis, long holdWarnMillis, Supplier<T> supplier) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new BizException("分布式锁key不能为空");
        }
        if (supplier == null) {
            throw new BizException("分布式锁业务逻辑不能为空");
        }

        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        long threadId = Thread.currentThread().getId();
        LockContext context = null;

        try {
            // leaseTime = -1：启用 watchdog，持有期间自动续期
            locked = lock.tryLock(waitTimeMillis, -1, TimeUnit.MILLISECONDS);

            if (!locked) {
                lockAuditRecorder.record(EVENT_ACQUIRE_FAILED, lockKey, null, 0L);
                log.warn("获取分布式锁失败，lockKey={}", lockKey);
                throw new BizException("系统繁忙，请勿重复提交");
            }

            // 全局单调递增围栏令牌：避免锁过期后旧持有者覆盖新数据
            long fenceToken = nextFenceToken();
            context = FenceTokenHolder.acquire(lockKey, fenceToken);
            lockAuditRecorder.record(EVENT_ACQUIRED, lockKey, fenceToken, 0L);
            log.debug("获取分布式锁成功，lockKey={}，fenceToken={}", lockKey, fenceToken);

            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断，lockKey={}", lockKey, e);
            throw new BizException("获取分布式锁被中断", e);
        } finally {
            // 解耦锁释放与 FenceTokenHolder：无论 context 是否成功 acquire，
            // 只要 tryLock 成功拿到了锁（locked=true），这里必须释放；
            // 否则 leaseTime=-1 的 Watchdog 会每 10 秒自动续期，锁在当前 JVM 存活期间永久泄漏。
            long heldMillis = 0L;
            long fenceToken = 0L;
            if (context != null) {
                heldMillis = context.heldMillis();
                fenceToken = context.getFenceToken();
                FenceTokenHolder.release();
            }
            if (locked && lock.isHeldByThread(threadId)) {
                try {
                    lock.unlock();
                    log.debug("释放分布式锁成功，lockKey={}", lockKey);
                } catch (Exception unlockEx) {
                    log.warn("释放分布式锁异常，lockKey={}", lockKey, unlockEx);
                }
            }
            if (context != null) {
                if (holdWarnMillis > 0 && heldMillis >= holdWarnMillis) {
                    log.warn("分布式锁持有时间过长，lockKey={}，heldMs={}，阈值={}", lockKey, heldMillis, holdWarnMillis);
                }
                lockAuditRecorder.record(EVENT_RELEASED, lockKey, fenceToken, heldMillis);
            }
        }
    }

    /**
     * 全局单调递增围栏令牌。
     * <p>
     * 令牌必须跨锁键可比：同一聚合会被不同业务键的锁先后推进（支付→发货→收货→退款申请→退款受理），
     * 若按锁键各自计数，新锁首次拿到的令牌可能小于行上已有令牌，合法的 CAS 更新会被静默丢弃。
     * 首次使用时以当前毫秒时间初始化，避免回退到历史已写入的小令牌区间。
     */
    private long nextFenceToken() {
        Long token = redissonClient.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE, FENCE_SEED_SCRIPT, RScript.ReturnType.INTEGER,
            Collections.singletonList(FENCE_KEY),
            String.valueOf(System.currentTimeMillis()), String.valueOf(FENCE_TTL_SECONDS));
        return token == null ? 0L : token;
    }
}
