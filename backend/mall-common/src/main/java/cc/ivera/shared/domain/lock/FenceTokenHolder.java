package cc.ivera.shared.domain.lock;

/**
 * 当前线程持有的分布式锁围栏令牌。
 * <p>
 * 加锁成功后由 {@link DistributedLockTemplate} 写入，业务事务内的 Repository 实现读取并写入 Mapper，
 * 用于在数据库 UPDATE 时做 {@code fence_token <= newToken} 条件更新，防止锁过期（异步复制延迟、进程停顿）后
 * 旧持有者覆盖新持有者的数据。
 * <p>
 * 状态由 {@link LockContext} 的线程栈承载：支持嵌套加锁，令牌取嵌套期间的最大值；仅最外层释放时清理。
 * 这样内层锁不会清空外层的令牌，也不会让外层回退到更小的令牌，避免后续合法 CAS 被静默拒绝。
 */
public final class FenceTokenHolder {

    private FenceTokenHolder() {
    }

    /**
     * 加锁成功时调用。嵌套时以调用栈顶的令牌为准，保证同一线程内令牌单调不减。
     *
     * @return 本次加锁的上下文，供调用方计算持有耗时与写入审计
     */
    public static LockContext acquire(String lockKey, long fenceToken) {
        LockContext context = LockContext.begin(lockKey, fenceToken);
        context.locked();
        return context;
    }

    /**
     * 释放锁时调用。仅最外层释放时清理，避免内层锁退出后外层丢失围栏令牌。
     */
    public static void release() {
        LockContext.end();
    }

    /**
     * @return 当前锁的围栏令牌；未持有锁时返回 null
     */
    public static Long get() {
        return LockContext.currentFenceToken();
    }
}
