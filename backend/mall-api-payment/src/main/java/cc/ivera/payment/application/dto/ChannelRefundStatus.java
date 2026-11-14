package cc.ivera.payment.application.dto;

import cc.ivera.payment.domain.enums.ChannelRefundState;
import lombok.Data;

/**
 * 渠道退款查询结果：供退款上下文展示或转换为退款状态同步结果。
 */
@Data
public class ChannelRefundStatus {

    private String channelStatus;

    private ChannelRefundState state;

    private String rawBody;

    private Integer totalFee;

    private Integer refundAmount;

    private String channelRefundId;
}
