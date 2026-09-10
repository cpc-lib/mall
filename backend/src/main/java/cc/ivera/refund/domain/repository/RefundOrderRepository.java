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
     */
    void update(RefundOrder refundOrder);

    /**
     * 按退款单号查询（渠道失败回查/幂等监听/状态机读用）。
     */
    RefundOrder findByRefundNo(String refundNo);

    /**
     * 幂等查询冲正单：订单号 + 申请来源（SYSTEM_DUPLICATE/SYSTEM_LATE/SYSTEM_OVERSOLD）。
     */
    RefundOrder findByOrderNoAndApplyType(String orderNo, String applyType);

    /**
     * 用户退款单按创建时间倒序。
     */
    List<RefundOrder> listByUserIdCreateTimeDesc(Long userId);

    /**
     * 全量退款单按创建时间倒序。
     */
    List<RefundOrder> listAllCreateTimeDesc();

    /**
     * 账账核对批量查询：按退款单号集合加载平台退款账。
     */
    List<RefundOrder> listByRefundNos(Collection<String> refundNos);

    /**
     * 账账核对日切：按退款成功时间加载平台成功退款账。
     */
    List<RefundOrder> listSuccessBySuccessTimeRange(Date startInclusive, Date endExclusive);
}
