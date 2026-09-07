package cc.ivera.vo;

import lombok.Data;

/**
 * 用户端支付结果主动查单响应 VO：渠道查单（已支付成功则同步本地订单）后的本地状态。
 */
@Data
public class PayQueryVO {

    private String orderNo;

    /** 本地支付状态（渠道确认成功后已被同步推进）：PAID / UNPAID */
    private String payStatus;

    /** 本地订单生命周期状态 */
    private String orderStatus;

    /** 支付渠道：WXPAY / ALIPAY */
    private String channelCode;

    /** 渠道侧交易状态（微信 trade_state / 支付宝 trade_status），无法确认时 UNKNOWN */
    private String channelTradeState;

    /** 渠道状态中文描述 */
    private String channelTradeStateDesc;

    /** 本次查单是否推进了本地订单状态 */
    private Boolean synced;
}
