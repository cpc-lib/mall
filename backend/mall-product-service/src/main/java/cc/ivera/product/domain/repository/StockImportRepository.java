package cc.ivera.product.domain.repository;

import cc.ivera.product.domain.model.StockImport;

import java.util.Date;
import java.util.List;

/**
 * 库存导入批次聚合仓储。
 */
public interface StockImportRepository {

    void insert(StockImport record);

    void update(StockImport record);

    void deleteById(Long id);

    StockImport findById(Long id);

    /**
     * 导入记录总数（管理员分页用）。
     */
    long countAll();

    /**
     * 导入记录分页：创建时间倒序，limit/offset 由调用方按页计算。
     */
    List<StockImport> listPage(int limit, long offset);

    /**
     * 编辑保存明细：仅 PENDING 可更新（CAS），返回是否命中。
     */
    boolean updatePendingItems(Long id, int itemCount, String itemsJson, Date now);

    /**
     * 确认入库抢占：PENDING→CONFIRMED（CAS），返回是否命中。
     */
    boolean casConfirm(Long id, Date confirmTime);
}
