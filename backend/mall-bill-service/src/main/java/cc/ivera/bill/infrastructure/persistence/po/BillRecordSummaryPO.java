package cc.ivera.bill.infrastructure.persistence.po;

import lombok.Data;

/**
 * 批次流水聚合查询结果（非表实体）：按 record_type 汇总笔数与金额。
 */
@Data
public class BillRecordSummaryPO {

    private Long payCount;
    private Long payAmount;
    private Long refundCount;
    private Long refundAmount;
}
