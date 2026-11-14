package cc.ivera.order.domain.repository;

import cc.ivera.order.domain.model.OrderRequest;

import java.util.Date;

/**
 * 下单请求幂等仓储端口。
 */
public interface OrderRequestRepository {

    OrderRequest find(String idemKey, Long userId, String api);

    /**
     * 插入 PROCESSING 记录；唯一键冲突时抛出 DuplicateKeyException。
     */
    void insert(OrderRequest request);

    int markSuccess(Long id, String orderNo, String responseBody);

    int markFailed(Long id);

    int casFailedToProcessing(Long id, String requestHash);

    /**
     * 删除过期记录，最多 limit 条。
     */
    int deleteExpired(Date now, int limit);
}
