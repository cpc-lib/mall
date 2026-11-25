package cc.ivera.bill.domain.policy;

/**
 * 对账平衡规则：渠道账 vs 平台交易账的日切平衡判定。
 * <p>balanced=true 必须同时满足：
 * 支付笔数/金额一致、退款笔数/金额一致、净额一致、且不存在 OPEN 差异单。
 * 净额 = 支付成功金额 - 退款成功金额（不含手续费）。
 */
public final class BillBalancedRule {

    private BillBalancedRule() {
    }

    public static boolean isBalanced(
        long channelPayCount, long localPayCount,
        long channelRefundCount, long localRefundCount,
        long channelPayAmount, long localPayAmount,
        long channelRefundAmount, long localRefundAmount,
        long channelNet, long localNet,
        long openDiscrepancyCount) {

        return channelPayCount == localPayCount
            && channelRefundCount == localRefundCount
            && channelPayAmount == localPayAmount
            && channelRefundAmount == localRefundAmount
            && channelNet == localNet
            && openDiscrepancyCount == 0;
    }
}
