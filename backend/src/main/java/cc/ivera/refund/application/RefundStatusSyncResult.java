package cc.ivera.refund.application;

import cc.ivera.refund.domain.enums.RefundStatus;
import lombok.Getter;

/**
 * 渠道退款状态同步结果（支付渠道 → 退款上下文 的边界值对象）：
 * 支付域不依赖退款实体，仅回传查单/通知解析出的退款状态与金额。
 */
@Getter
public class RefundStatusSyncResult {

    private final String orderNo;

    private final String refundNo;

    private final String refundId;

    private final String channelStatus;

    private final RefundStatus refundStatus;

    private final String content;

    private final Integer totalFee;

    private final Integer refundAmount;

    private RefundStatusSyncResult(String orderNo,
                                   String refundNo,
                                   String refundId,
                                   String channelStatus,
                                   RefundStatus refundStatus,
                                   String content,
                                   Integer totalFee,
                                   Integer refundAmount) {
        this.orderNo = orderNo;
        this.refundNo = refundNo;
        this.refundId = refundId;
        this.channelStatus = channelStatus;
        this.refundStatus = refundStatus;
        this.content = content;
        this.totalFee = totalFee;
        this.refundAmount = refundAmount;
    }

    public static RefundStatusSyncResult of(String orderNo,
                                            String refundNo,
                                            String refundId,
                                            String channelStatus,
                                            RefundStatus refundStatus,
                                            String content) {
        return of(orderNo, refundNo, refundId, channelStatus, refundStatus, content, null, null);
    }

    public static RefundStatusSyncResult of(String orderNo,
                                            String refundNo,
                                            String refundId,
                                            String channelStatus,
                                            RefundStatus refundStatus,
                                            String content,
                                            Integer totalFee,
                                            Integer refundAmount) {
        return new RefundStatusSyncResult(
            orderNo,
            refundNo,
            refundId,
            channelStatus,
            refundStatus,
            content,
            totalFee,
            refundAmount);
    }

    public boolean hasRefundStatus() {
        return refundStatus != null;
    }
}
