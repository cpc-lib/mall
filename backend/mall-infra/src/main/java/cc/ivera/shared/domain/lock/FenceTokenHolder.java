package cc.ivera.shared.domain.lock;

/**
 * 当前线程持有的分布式锁围栏令牌。
 * <p>
 * 加锁成功后由 {@link DistributedLockTemplate} 写入，业务事务内的 Repository 实现读取并写入 Mapper，
 * 用于在数据库 UPDATE 时做 {@code fence_token < newToken} 条件更新，防止锁过期（异步复制延迟、进程停顿）后
 * 旧持有者覆盖新持有者的数据。
 */
public final class FenceTokenHolder {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private FenceTokenHolder() {
    }

    public static void set(long fenceToken) {
        HOLDER.set(fenceToken);
    }

    /**
     * @return 当前锁的围栏令牌；未持有锁时返回 null
     */
    public static Long get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
