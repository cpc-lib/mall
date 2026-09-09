package cc.ivera.payment.interfaces.vo;

import lombok.Data;

/**
 * 管理端渠道订单查询结果 VO：展示渠道侧交易状态并记录本地状态是否被同步推进。
 */
@Data
public class ChannelOrderQueryVO {

    private String orderNo;

    /**
     * 渠道编码：WXPAY / ALIPAY
     */
    private String channelCode;

    /**
     * 渠道侧交易状态（微信 trade_state / 支付宝 trade_status），无法确认时 UNKNOWN
     */
    private String channelTradeState;

    /**
     * 渠道状态中文描述
     */
    private String channelTradeStateDesc;

    /**
     * 查单前本地订单状态（order_status）
     */
    private String localOrderStatusBefore;

    /**
     * 查单后本地订单状态（order_status）
     */
    private String localOrderStatusAfter;

    /**
     * 查单后本地支付状态（pay_status）
     */
    private String localPayStatusAfter;

    /**
     * 本次查单是否推进了本地订单状态
     */
    private Boolean synced;

    /**
     * 渠道原始报文 JSON 字符串
     */
    private String channelRawBody;
}
