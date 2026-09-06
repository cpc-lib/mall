package cc.ivera.service;

import cc.ivera.entity.Product;
import cc.ivera.vo.InventoryTransactionVO;
import cc.ivera.vo.PageVO;

import java.util.List;

/**
 * 商品库存调整与库存流水查询（V2 审计源：t_inventory_transaction）。
 */
public interface ProductStockService {

    /**
     * 单品库存调整（原子单元，独立事务）：CAS 更新 available_stock + 落 MANUAL_ADJUST 流水。
     * 注意：失败时由调用方通过 {@link #recordFailedAdjustment} 记录 FAILED 流水。
     *
     * @param productId 商品ID
     * @param delta     调整量（正数补货、负数扣减），0 非法
     * @return 调整后的商品
     * @throws cc.ivera.exception.BizException 商品不存在 / 调整量为 0 / 扣减后为负数
     */
    Product adjustOne(Long productId, int delta);

    /**
     * 记录一次失败的调整申请（独立事务）：落 FAILED 流水（available_delta=0，库存不变化），
     * error_message 记录申请调整量与拒绝原因。
     */
    void recordFailedAdjustment(Long productId, int delta, String reason);

    /** 商品最近 limit 条库存流水（详情页展示，按 create_time desc, id desc） */
    List<InventoryTransactionVO> recentLogs(Long productId, int limit);

    /**
     * 库存流水分页查询（管理端审计/维护页）。
     *
     * @param page     页码（>=1）
     * @param size     每页条数（1..100）
     * @param productId 商品过滤（可空）
     * @param bizType   类型过滤（可空，如 MANUAL_ADJUST / ORDER_RESERVE）
     * @param status    操作状态过滤（可空，SUCCESS / FAILED）
     * @return 分页结果，records 行结构与 recentLogs 一致
     */
    PageVO<InventoryTransactionVO> pageTransactions(int page, int size, Long productId, String bizType, String status);
}
