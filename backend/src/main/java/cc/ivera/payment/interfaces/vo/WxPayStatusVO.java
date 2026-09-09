package cc.ivera.payment.interfaces.vo;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付状态查询响应 VO。
 */
@Data
public class WxPayStatusVO {

    private String orderNo;

    private String tradeState;

    private String tradeStateDesc;

    private String localStatusBefore;

    private String localStatus;

    /**
     * 微信支付渠道原始响应
     */
    private Map<String, Object> wxPayResult;
}
