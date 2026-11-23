package cc.ivera.shared.domain.lock;

import cc.ivera.shared.domain.exception.BizException;

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
     * 加锁并执行业务，同时指定持有告警阈值。
     *
     * @param holdWarnMillis 持有超过该毫秒数时记录 WARN 并写入审计；{@code <= 0} 表示关闭
     */
    <T> T execute(String lockKey, long waitTimeMillis, long holdWarnMillis, Supplier<T> supplier);

    /**
     * 加锁并执行无返回值业务。
     */
    default void execute(String lockKey, long waitTimeMillis, Runnable runnable) {
        execute(lockKey, waitTimeMillis, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 尝试非阻塞抢锁并执行：抢不到静默返回 false，不抛异常。
     * 用于定时任务/兜底扫描等场景——集群多节点只允许一个实例执行，抢不到跳过不重试。
     *
     * @param lockKey  锁 key
     * @param runnable 加锁成功后执行的业务逻辑
     * @return {@code true} 抢到锁并执行；{@code false} 未抢到锁（其他实例持有或锁被占用）
     */
    default boolean tryExecute(String lockKey, Runnable runnable) {
        try {
            execute(lockKey, 0L, runnable);
            return true;
        } catch (BizException acquireFailed) {
            // execute 抢不到锁会抛 BizException；吞掉让调用方自行决定是否日志记录
            return false;
        }
    }
}
