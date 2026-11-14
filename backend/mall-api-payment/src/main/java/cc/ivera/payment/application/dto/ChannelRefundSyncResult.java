package cc.ivera.payment.application.dto;

import cc.ivera.payment.domain.enums.ChannelRefundState;
import lombok.Data;

/**
 * 渠道退款状态同步结果：支付渠道查单/通知解析后回传，不含退款实体。
 */
@Data
public class ChannelRefundSyncResult {

    private String orderNo;

    private String refundNo;

    private String channelRefundId;

    private String channelStatus;

    private ChannelRefundState state;

    private String content;

    private Integer totalFee;

    private Integer refundAmount;

    public static ChannelRefundSyncResult of(String orderNo,
                                             String refundNo,
                                             String channelRefundId,
                                             String channelStatus,
                                             ChannelRefundState state,
                                             String content,
                                             Integer totalFee,
                                             Integer refundAmount) {
        ChannelRefundSyncResult result = new ChannelRefundSyncResult();
        result.setOrderNo(orderNo);
        result.setRefundNo(refundNo);
        result.setChannelRefundId(channelRefundId);
        result.setChannelStatus(channelStatus);
        result.setState(state);
        result.setContent(content);
        result.setTotalFee(totalFee);
        result.setRefundAmount(refundAmount);
        return result;
    }
}
