package cc.ivera.refund.domain.repository;

import cc.ivera.refund.domain.model.RefundItem;

import java.util.List;

/**
 * 退款明细仓储端口（RefundOrder 聚合内明细）。
 */
public interface RefundItemRepository {

    /**
     * 新建退款明细（id 回填）。
     */
    void save(RefundItem item);

    /**
     * 退款单下全部明细按 id 升序。
     */
    List<RefundItem> listByRefundNoAsc(String refundNo);

    /**
     * 删除退款单下全部明细（编辑申请时重建）。
     */
    void deleteByRefundNo(String refundNo);
}
