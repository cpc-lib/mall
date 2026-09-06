package cc.ivera.lock;

import java.util.function.Supplier;

/**
 * 分布式锁模板。
 *
 * 用途：把“加锁、执行业务、释放锁”的样板代码统一封装，避免业务层到处写 Redis/Zookeeper 细节。
 */
public interface DistributedLockTemplate {

    /**
     * 加锁并执行业务。
     *
     * @param lockKey         锁 key
     * @param waitTimeMillis  获取锁最大等待时间，单位毫秒
     * @param leaseTimeMillis 锁自动过期时间，单位毫秒
     * @param supplier        加锁成功后执行的业务逻辑
     * @param <T>             返回值类型
     * @return 业务返回值
     */
    <T> T execute(String lockKey, long waitTimeMillis, long leaseTimeMillis, Supplier<T> supplier);

    /**
     * 加锁并执行无返回值业务。
     */
    default void execute(String lockKey, long waitTimeMillis, long leaseTimeMillis, Runnable runnable) {
        execute(lockKey, waitTimeMillis, leaseTimeMillis, () -> {
            runnable.run();
            return null;
        });
    }
}
