package cc.ivera.bill.domain.repository;

import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.domain.model.BillRecordSummary;

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
     * 批量新建流水：单条 INSERT 多值按批提交，不逐行回填主键。
     */
    void saveBatch(List<BillRecord> billRecords);

    /**
     * 批次明细列表：importId 必填，recordType 非空时按记录类型过滤，按 id 升序。
     */
    List<BillRecord> listByImport(Long importId, String recordType);

    /**
     * 对账比对用批次明细：不读取 raw_line CLOB，按 id 升序。
     */
    List<BillRecord> listForReconcile(Long importId);

    /**
     * 批次流水聚合：按记录类型汇总笔数与金额，避免加载全量流水。
     */
    BillRecordSummary summarizeByImport(Long importId);

    /**
     * 差异下钻定位渠道流水：按业务单号或渠道流水号任一相等匹配，空白条件不参与，
     * 避免把整批明细加载到内存后再过滤。结果按 id 升序。
     */
    List<BillRecord> listByImportAndMatch(Long importId, String recordType, String bizNo, String channelSerialNo);

    /**
     * 批次明细总数（同一过滤条件）。
     */
    long countByImport(Long importId, String recordType);

    /**
     * 批次明细分页：id 升序，limit/offset 由调用方按页计算。
     */
    List<BillRecord> listPageByImport(Long importId, String recordType, int limit, long offset);
}
