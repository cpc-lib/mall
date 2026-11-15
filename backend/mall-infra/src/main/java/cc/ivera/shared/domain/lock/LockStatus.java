package cc.ivera.shared.domain.lock;

/**
 * 当前线程锁上下文的状态。
 */
public enum LockStatus {
    ACQUIRING,
    LOCKED,
    RELEASED
}
