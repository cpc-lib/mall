package cc.ivera.bill.domain.model;

import lombok.Data;

/**
 * 账账核对汇总：渠道账 vs 平台交易账的日切平衡结果。
 * 金额单位统一为分；净额 = 支付成功金额 - 退款成功金额（暂不含手续费/资金账）。
 */
@Data
public class BillReconcileSummary {
    private String importNo;
    private String billDate;
    private String channelCode;
    private String billKind;

    private Integer channelPayCount;
    private Integer localPayCount;
    private Long channelPayAmount;
    private Long localPayAmount;

    private Integer channelRefundCount;
    private Integer localRefundCount;
    private Long channelRefundAmount;
    private Long localRefundAmount;

    private Long channelNetAmount;
    private Long localNetAmount;
    private Long netDifference;

    private Integer matchedCount;
    private Integer discrepancyCount;
    private Integer openDiscrepancyCount;
    private Boolean balanced;
}
