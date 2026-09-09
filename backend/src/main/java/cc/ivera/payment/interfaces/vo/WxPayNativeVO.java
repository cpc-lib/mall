package cc.ivera.payment.interfaces.vo;

import lombok.Data;

/**
 * 微信 Native 下单响应 VO（二维码链接 + 订单号）。
 */
@Data
public class WxPayNativeVO {

    private String codeUrl;

    private String orderNo;
}
