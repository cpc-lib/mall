package cc.ivera.order.application;

import java.util.Date;

/**
 * Seata Saga 历史实例清理应用服务。
 * <p>仅清理超过保留期的成功终态根实例，单批事务内原子删除明细与实例。</p>
 */
public interface SagaHistoryCleanupService {

    /**
     * 清理一批过期成功终态 Saga 实例。
     *
     * @param cutoff 只删除 gmt_end 早于此时间的实例
     * @return 实际删除的实例数；0 表示当前无可清理实例
     */
    int cleanBatch(Date cutoff);
}
