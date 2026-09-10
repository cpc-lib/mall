package cc.ivera.bill.interfaces.vo;

import cc.ivera.bill.domain.model.BillReconcileSummary;
import lombok.Data;

/**
 * 账账核对汇总视图。金额单位为分。
 */
@Data
public class BillReconcileSummaryVO {
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

    public static BillReconcileSummaryVO from(BillReconcileSummary source) {
        if (source == null) {
            return null;
        }
        BillReconcileSummaryVO vo = new BillReconcileSummaryVO();
        vo.setImportNo(source.getImportNo());
        vo.setBillDate(source.getBillDate());
        vo.setChannelCode(source.getChannelCode());
        vo.setBillKind(source.getBillKind());
        vo.setChannelPayCount(source.getChannelPayCount());
        vo.setLocalPayCount(source.getLocalPayCount());
        vo.setChannelPayAmount(source.getChannelPayAmount());
        vo.setLocalPayAmount(source.getLocalPayAmount());
        vo.setChannelRefundCount(source.getChannelRefundCount());
        vo.setLocalRefundCount(source.getLocalRefundCount());
        vo.setChannelRefundAmount(source.getChannelRefundAmount());
        vo.setLocalRefundAmount(source.getLocalRefundAmount());
        vo.setChannelNetAmount(source.getChannelNetAmount());
        vo.setLocalNetAmount(source.getLocalNetAmount());
        vo.setNetDifference(source.getNetDifference());
        vo.setMatchedCount(source.getMatchedCount());
        vo.setDiscrepancyCount(source.getDiscrepancyCount());
        vo.setOpenDiscrepancyCount(source.getOpenDiscrepancyCount());
        vo.setBalanced(source.getBalanced());
        return vo;
    }
}
