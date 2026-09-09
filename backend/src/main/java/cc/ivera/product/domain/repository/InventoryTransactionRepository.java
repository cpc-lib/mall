package cc.ivera.product.domain.repository;

import cc.ivera.product.domain.model.InventoryTransaction;

import java.util.List;

/**
 * 库存流水记录型仓储端口：仅追加（insert）与查询，无独立生命周期管理。
 * biz_no 唯一键兜底幂等（重复追加由调用方捕获 DuplicateKeyException 跳过）。
 */
public interface InventoryTransactionRepository {

    void append(InventoryTransaction transaction);

    int countByBizNo(String bizNo);

    /**
     * 库存流水总数：商品/业务类型/操作状态均为可选过滤条件。
     */
    long countTransactions(Long productId, String bizType, String status);

    /**
     * 库存流水分页：条件可选，按创建时间、id 倒序，LIMIT/OFFSET 手动分页。
     */
    List<InventoryTransaction> pageTransactions(Long productId, String bizType, String status, int limit, long offset);

    /**
     * 商品最近 limit 条库存流水（按 create_time desc, id desc）。
     */
    List<InventoryTransaction> recentByProduct(Long productId, int limit);
}
