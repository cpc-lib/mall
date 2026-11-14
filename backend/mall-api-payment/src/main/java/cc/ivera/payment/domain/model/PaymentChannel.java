package cc.ivera.payment.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 支付渠道（t_payment_channel）：渠道级公共参数（domain/gatewayUrl/contentKey/notifyUrl/returnUrl）
 * 与商户凭证（微信 appid/商户号/证书序列号/私钥/API密钥；支付宝 appId/卖家PID/私钥/公钥）。
 */
@Data
public class PaymentChannel {

    private Long id;

    /**
     * 渠道名称
     */
    private String channelName;

    /**
     * 渠道编码
     */
    private String channelCode;

    /**
     * 渠道状态：ENABLED-启用，DISABLED-禁用
     */
    private String channelStatus;

    /**
     * 渠道描述
     */
    private String channelDesc;

    /**
     * 配置参数（JSON格式，渠道公共参数：domain、gatewayUrl、contentKey、notifyUrl、returnUrl）
     */
    private String configParams;

    // ========== 微信商户信息（WXPAY 渠道） ==========

    /**
     * 微信appid（公众号/小程序/APP）
     */
    private String appid;

    /**
     * 微信商户号
     */
    private String mchId;

    /**
     * 微信商户API证书序列号
     */
    private String mchSerialNo;

    /**
     * 微信商户私钥PEM内容（替代私钥文件）
     */
    private String privateKey;

    /**
     * 微信APIv3密钥
     */
    private String apiV3Key;

    /**
     * 微信APIv2密钥
     */
    private String partnerKey;

    // ========== 支付宝商户信息（ALIPAY 渠道） ==========

    /**
     * 支付宝应用ID
     */
    private String alipayAppId;

    /**
     * 支付宝卖家PID
     */
    private String sellerId;

    /**
     * 支付宝应用私钥（base64）
     */
    private String merchantPrivateKey;

    /**
     * 支付宝公钥（base64）
     */
    private String alipayPublicKey;

    /**
     * 排序号
     */
    private Integer sortOrder;

    private Date createTime;

    private Date updateTime;
}
