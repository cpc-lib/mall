package cc.ivera.payment.application.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 渠道退款请求（退款上下文 → 支付渠道 的边界 DTO）：只承载渠道调用所需字段，不含退款实体。
 */
@Data
public class ChannelRefundCommand implements Serializable {

    private String refundNo;

    private String orderNo;

    private String paymentNo;

    private String channel;

    private Integer totalFee;

    private Integer refundAmount;

    private String reason;
}
