package cc.ivera.bill.domain.repository;

import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;

import java.util.List;

/**
 * 账单对账差异单仓储端口。
 */
public interface BillReconcileDiscrepancyRepository {

    /**
     * 新建差异单（id/审计时间回填）。
     */
    void save(BillReconcileDiscrepancy discrepancy);

    /**
     * 按主键更新（MP NOT_NULL 策略）。
     */
    void update(BillReconcileDiscrepancy discrepancy);

    BillReconcileDiscrepancy findById(Long id);

    /**
     * 重跑对账前按批次删除旧差异单。
     */
    void deleteByImport(Long importId);

    /**
     * 批次差异列表：importId 必填，bizType/discrepancyType/status 非空时分别过滤，按 id 升序。
     */
    List<BillReconcileDiscrepancy> listByImport(Long importId, String bizType,
                                                String discrepancyType, String status);
}
