package cc.ivera.bill.domain.model;

import lombok.Data;

/**
 * 批次流水聚合：由数据库直接按记录类型汇总，避免把全量流水（含 raw_line CLOB）加载到内存。
 * 退款笔数/金额只统计已结算（SUCCESS/REFUND/REVOKED）的退款行。
 */
@Data
public class BillRecordSummary {

    private long payCount;
    private long payAmount;
    private long refundCount;
    private long refundAmount;
}
