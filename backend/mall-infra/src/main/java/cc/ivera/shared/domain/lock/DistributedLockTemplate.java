package cc.ivera.shared.domain.lock;

import java.util.function.Supplier;

/**
 * 分布式锁模板。
 * <p>
 * 统一使用 Redisson watchdog 机制（leaseTime = -1，持有期间自动续期）。
 * 加锁成功后通过 Redis {@code INCR lock:fence:{lockKey}} 生成单调递增的围栏令牌，
 * 写入 {@link FenceTokenHolder}，供业务事务内的 Mapper 做围栏 CAS。
 */
public interface DistributedLockTemplate {

    /**
     * 加锁并执行业务。锁租约由 watchdog 自动续期，调用方无需指定过期时间。
     *
     * @param lockKey        锁 key
     * @param waitTimeMillis 获取锁最大等待时间，单位毫秒
     * @param supplier       加锁成功后执行的业务逻辑，可通过 {@link FenceTokenHolder#get()} 读取围栏令牌
     * @param <T>            返回值类型
     * @return 业务返回值
     */
    <T> T execute(String lockKey, long waitTimeMillis, Supplier<T> supplier);

    /**
     * 加锁并执行无返回值业务。
     */
    default void execute(String lockKey, long waitTimeMillis, Runnable runnable) {
        execute(lockKey, waitTimeMillis, () -> {
            runnable.run();
            return null;
        });
    }
}
