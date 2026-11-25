package cc.ivera.shared.domain.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 围栏令牌 ThreadLocal 栈语义：
 * <ul>
 *   <li>acquire 入栈 / release 出栈 / 栈空清理 ThreadLocal；</li>
 *   <li>嵌套加锁时内层令牌取栈顶（单调不减，不会让外层回退到更小的令牌）；</li>
 *   <li>多线程隔离：ThreadLocal 不得串线程；</li>
 *   <li>栈空 release 幂等，不得抛异常。</li>
 * </ul>
 */
class FenceTokenHolderTest {

    @Test
    @DisplayName("单次 acquire → get 拿到 token → release 后 get 变 null")
    void acquireReleaseCycle() {
        LockContext ctx = FenceTokenHolder.acquire("ORDER:1", 42L);
        assert ctx != null;
        assertEquals("ORDER:1", ctx.getLockKey());
        assertEquals(42L, ctx.getFenceToken());
        assertEquals(42L, FenceTokenHolder.get());
        FenceTokenHolder.release();
        assertNull(FenceTokenHolder.get(), "release 后栈空应返回 null");
    }

    @Test
    @DisplayName("嵌套锁：内层令牌单调不减，内层 release 后回到外层令牌")
    void nestedLocksPreserveMaxFence() {
        FenceTokenHolder.acquire("ORDER:1", 10L);
        assertEquals(10L, FenceTokenHolder.get());
        // 内层锁拿到更大的 fence token，栈顶应立即变成 20
        FenceTokenHolder.acquire("ORDER:1", 20L);
        assertEquals(20L, FenceTokenHolder.get(), "内层加锁后应取栈顶的 20");
        // 内层释放 → 回退到外层的 10（不是更小，而是栈里本来就是 10）
        FenceTokenHolder.release();
        assertEquals(10L, FenceTokenHolder.get(), "内层 release 后应回到外层的 10");
        FenceTokenHolder.release();
        assertNull(FenceTokenHolder.get());
    }

    @Test
    @DisplayName("多线程隔离：外层线程 release 不影响内层线程的 ThreadLocal")
    void multiThreadIsolation() throws Exception {
        LockContext outer = FenceTokenHolder.acquire("ORDER:1", 100L);
        Long[] innerSeen = new Long[1];
        Thread t = new Thread(() -> {
            FenceTokenHolder.acquire("ORDER:2", 200L);
            innerSeen[0] = FenceTokenHolder.get();
            FenceTokenHolder.release();
        });
        t.start();
        t.join();

        assertEquals(100L, FenceTokenHolder.get(), "原线程 token 未被干扰");
        assertEquals(200L, innerSeen[0], "子线程拿到自己的 fence");
        FenceTokenHolder.release();
        assertNull(FenceTokenHolder.get());
    }

    @Test
    @DisplayName("栈空 release 幂等，不得抛异常")
    void idempotentReleaseOnEmptyStack() {
        FenceTokenHolder.release(); // 第一次空栈
        FenceTokenHolder.release(); // 第二次也应安全
        assertNull(FenceTokenHolder.get());
    }

    @Test
    @DisplayName("LockContext.heldMillis 应大于等于 0（刚拿到锁时为 0 或很小正数）")
    void heldMillisNonNegative() throws Exception {
        LockContext ctx = FenceTokenHolder.acquire("ORDER:1", 1L);
        // System.nanoTime 粒度下 heldMillis 可能为 0，这也是合法值
        long held = ctx.heldMillis();
        assert held >= 0 : "heldMillis 不得为负，实际 " + held;
        FenceTokenHolder.release();
    }
}
