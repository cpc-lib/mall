package cc.ivera.shared.domain.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式分布式锁：标注在需要串行化的方法上，由切面在方法执行前加锁、执行后释放，
 * 持有期间写入 {@link FenceTokenHolder} 围栏令牌，与编程式 {@link DistributedLockTemplate} 语义一致。
 * <p>
 * {@link #key()} 支持 SpEL，可用变量为方法参数名（编译已开启 {@code -parameters}）与
 * {@code #p0} / {@code #a0} 形式的位置参数，例如：
 * <pre>{@code
 * @DistributedLock(key = "'payment:order:' + #orderNo")
 * public void handlePaid(String orderNo) { ... }
 * }</pre>
 * 不含 {@code #} 的表达式按字面量 key 使用。
 * <p>
 * 锁键按聚合根统一命名空间：同一订单的支付推进统一用 {@code payment:order:{orderNo}}，
 * 同一退款单统一用 {@code refund:order:{refundNo}}，避免同一聚合被不同锁键并发进入导致围栏令牌逆序误拒。
 * <p>
 * 注意：切面优先级高于事务通知，方法内的事务在锁持有期间提交。允许嵌套加锁：内层释放不会清除外层的围栏令牌，
 * 同一线程内令牌单调不减；但不同锁键交叉嵌套仍有按相反顺序争锁导致的等待超时风险，应尽量保持单一入口加锁。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁 key，支持 SpEL 表达式。
     */
    String key();

    /**
     * 获取锁的最大等待时间，单位毫秒。
     */
    long waitTimeMillis() default 3000L;

    /**
     * 持有超过该毫秒数时记录 WARN 日志并写入锁审计；{@code <= 0} 表示关闭告警。
     */
    long holdWarnMillis() default 5000L;
}
