package cc.ivera.shared.domain.lock;

/**
 * 分布式锁审计记录端口：记录加锁、释放与获取失败事件，供排障追溯。
 * <p>
 * 审计属于旁路能力，实现必须自行吞掉写入异常（记录日志），不得影响业务锁的获取与释放。
 */
public interface LockAuditRecorder {

    /**
     * @param event       事件类型，如 ACQUIRED / RELEASED / ACQUIRE_FAILED
     * @param lockKey     锁 key
     * @param fenceToken  围栏令牌，获取失败时为 null
     * @param heldMillis  持有毫秒数，获取事件为 0
     */
    void record(String event, String lockKey, Long fenceToken, long heldMillis);
}
