package cc.ivera.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道退款查询请求：退款上下文提供本地退款单信息，支付渠道负责远程查询。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelRefundQuery {

    private String refundNo;

    private String orderNo;

    private String channel;
}
