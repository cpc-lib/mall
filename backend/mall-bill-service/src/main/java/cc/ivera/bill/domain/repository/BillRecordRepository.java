package cc.ivera.bill.domain.repository;

import cc.ivera.bill.domain.model.BillRecord;

import java.util.List;

/**
 * 账单流水原始记录仓储端口。
 */
public interface BillRecordRepository {

    /**
     * 新建流水（id/审计时间回填）。
     */
    void save(BillRecord billRecord);

    /**
     * 批次明细列表：importId 必填，recordType 非空时按记录类型过滤，按 id 升序。
     */
    List<BillRecord> listByImport(Long importId, String recordType);

    /**
     * 批次明细总数（同一过滤条件）。
     */
    long countByImport(Long importId, String recordType);

    /**
     * 批次明细分页：id 升序，limit/offset 由调用方按页计算。
     */
    List<BillRecord> listPageByImport(Long importId, String recordType, int limit, long offset);
}
