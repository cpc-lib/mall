package cc.ivera.refund.domain.repository;

import cc.ivera.refund.domain.model.RefundOrder;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 退款单聚合仓储端口（聚合根 RefundOrder）。
 * 状态流转一律在事务内走 CAS 条件更新，禁止先改后查；并发互斥由调用方 Redis 分布式锁串行化。
 */
public interface RefundOrderRepository {

    /**
     * 新建退款单（id 回填）。
     */
    void save(RefundOrder refundOrder);

    /**
     * 按主键更新退款单（状态流转后持久化）。
     *
     * @deprecated 锁外调用方改用 {@link #updateStatusIfStatusIn(String, java.util.Collection, String)}
     * 或 {@link #markFailedIfStatusIn(String, java.util.Collection)} 等条件更新，避免脏对象覆盖。
     */
    @Deprecated
    void update(RefundOrder refundOrder);

    /**
     * 条件更新状态：仅当当前状态在 allowed 集合内时才更新为 newStatus，受 fence_token 围栏保护。
     * 用于锁外状态同步/对账回写，避免先读后改的脏对象覆盖。
     *
     * @return 实际更新行数；0 表示当前状态已不在 allowed 集合，跳过
     */
    int updateStatusIfStatusIn(String refundNo, java.util.Collection<String> allowed, String newStatus);

    /**
     * 条件置为 FAILED：仅当当前状态在 allowed 集合内时才更新 status + legacyApplyStatus 为 FAILED。
     * 用于 RefundQuotaReleasedListener 等锁外回调，避免覆盖后续链路已推进的状态。
     *
     * @return 实际更新行数；0 表示已被其他链路推进，跳过
     */
    int markFailedIfStatusIn(String refundNo, java.util.Collection<String> allowed);

    /**
     * 按退款单号查询（渠道失败回查/幂等监听/状态机读用）。
     */
    RefundOrder findByRefundNo(String refundNo);

    /**
     * 幂等查询冲正单：订单号 + 申请来源（SYSTEM_DUPLICATE/SYSTEM_LATE/SYSTEM_OVERSOLD）。
     */
    RefundOrder findByOrderNoAndApplyType(String orderNo, String applyType);

    /**
     * 幂等查询冲正单：支付单号 + 申请来源（SYSTEM_DUPLICATE/SYSTEM_LATE）。
     * 同一订单允许 1:N 支付尝试，冲正幂等锚必须是 paymentNo 而非 orderNo。
     */
    RefundOrder findByPaymentNoAndApplyType(String paymentNo, String applyType);

    /**
     * 订单退款单按创建时间倒序。
     */
    List<RefundOrder> listByOrderNoCreateTimeDesc(String orderNo);

    /**
     * 用户退款单分页：按创建时间倒序、id 倒序保证稳定翻页。
     */
    List<RefundOrder> listByUserIdPage(Long userId, int limit, long offset);

    /**
     * 用户退款单总数（与 {@link #listByUserIdPage} 口径一致）。
     */
    long countByUserId(Long userId);

    /**
     * 退款单总数（管理员分页用）。
     */
    long countAll();

    /**
     * 管理员分页：创建时间倒序，limit/offset 由调用方按页计算。
     */
    List<RefundOrder> listPageCreateTimeDesc(int limit, long offset);

    /**
     * 账账核对批量查询：按退款单号集合加载平台退款账。
     */
    List<RefundOrder> listByRefundNos(Collection<String> refundNos);

    /**
     * 账账核对日切：按退款成功时间加载平台成功退款账。
     */
    List<RefundOrder> listSuccessBySuccessTimeRange(Date startInclusive, Date endExclusive);
}
