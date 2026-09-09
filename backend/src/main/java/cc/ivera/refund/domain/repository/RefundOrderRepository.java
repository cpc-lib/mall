package cc.ivera.refund.domain.repository;

import cc.ivera.refund.domain.model.RefundOrder;

import java.util.List;

/**
 * 退款单聚合仓储端口（聚合根 RefundOrder）。
 * 状态流转一律先对退款单行加锁（findByRefundNoForUpdate）再在事务内更新，禁止先改后查。
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
     * 按退款单号查询并加行级排他锁。
     */
    RefundOrder findByRefundNoForUpdate(String refundNo);

    /**
     * 按退款单号查询（不加锁，渠道失败回查/幂等监听用）。
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
}
