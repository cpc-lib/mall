package cc.ivera.shared.domain.lock;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 当前线程的锁上下文栈：记录 lockKey、围栏令牌、加锁时刻与状态。
 * <p>
 * 嵌套加锁时按栈保存，{@link #currentFenceToken()} 取栈顶令牌。围栏令牌由 Redis 全局自增生成，
 * 内层加锁一定晚于外层，因此栈顶即嵌套期间的最大令牌，不会让外层回退到更小的令牌。
 * 仅最外层释放时清理 ThreadLocal，避免内层退出后外层丢失令牌。
 */
public final class LockContext {

    private static final ThreadLocal<Deque<LockContext>> STACK = new ThreadLocal<>();

    private final String lockKey;

    private final long fenceToken;

    private final long acquireNanos;

    private LockStatus status = LockStatus.ACQUIRING;

    private LockContext(String lockKey, long fenceToken) {
        this.lockKey = lockKey;
        this.fenceToken = fenceToken;
        this.acquireNanos = System.nanoTime();
    }

    /**
     * 入栈一个新的加锁上下文。
     */
    static LockContext begin(String lockKey, long fenceToken) {
        LockContext context = new LockContext(lockKey, fenceToken);
        stack().push(context);
        return context;
    }

    /**
     * 出栈当前加锁上下文；栈空时清理 ThreadLocal。
     */
    static void end() {
        Deque<LockContext> stack = STACK.get();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.pop().status = LockStatus.RELEASED;
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /**
     * @return 当前线程最内层的加锁上下文；未持有锁时返回 null
     */
    public static LockContext current() {
        Deque<LockContext> stack = STACK.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    /**
     * @return 当前锁的围栏令牌；未持有锁时返回 null
     */
    public static Long currentFenceToken() {
        LockContext context = current();
        return context == null ? null : context.fenceToken;
    }

    void locked() {
        status = LockStatus.LOCKED;
    }

    public String getLockKey() {
        return lockKey;
    }

    public long getFenceToken() {
        return fenceToken;
    }

    public LockStatus getStatus() {
        return status;
    }

    /**
     * @return 从加锁成功到当前时刻的持有毫秒数
     */
    public long heldMillis() {
        return (System.nanoTime() - acquireNanos) / 1_000_000L;
    }

    private static Deque<LockContext> stack() {
        Deque<LockContext> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        return stack;
    }
}
