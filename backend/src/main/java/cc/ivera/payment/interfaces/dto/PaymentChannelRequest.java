package cc.ivera.payment.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PaymentChannelRequest {

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 64, message = "渠道名称长度不能超过64个字符")
    private String channelName;

    @NotBlank(message = "渠道编码不能为空")
    @Size(max = 32, message = "渠道编码长度不能超过32个字符")
    private String channelCode;

    @Size(max = 16, message = "渠道状态长度不能超过16个字符")
    private String channelStatus;

    @Size(max = 255, message = "渠道描述长度不能超过255个字符")
    private String channelDesc;

    /**
     * 渠道公共参数，JSON 对象字符串，例如：{"domain":"https://api.mch.weixin.qq.com","notifyUrl":"..."}
     */
    private String configParams;

    // ========== 微信商户信息（WXPAY 渠道） ==========

    @Size(max = 64, message = "微信appid长度不能超过64个字符")
    private String appid;

    @Size(max = 32, message = "微信商户号长度不能超过32个字符")
    private String mchId;

    @Size(max = 64, message = "微信商户API证书序列号长度不能超过64个字符")
    private String mchSerialNo;

    /**
     * 微信商户私钥PEM内容
     */
    private String privateKey;

    @Size(max = 128, message = "微信APIv3密钥长度不能超过128个字符")
    private String apiV3Key;

    @Size(max = 128, message = "微信APIv2密钥长度不能超过128个字符")
    private String partnerKey;

    // ========== 支付宝商户信息（ALIPAY 渠道） ==========

    @Size(max = 64, message = "支付宝appId长度不能超过64个字符")
    private String alipayAppId;

    @Size(max = 64, message = "支付宝卖家PID长度不能超过64个字符")
    private String sellerId;

    /**
     * 支付宝应用私钥（base64）
     */
    private String merchantPrivateKey;

    /**
     * 支付宝公钥（base64）
     */
    private String alipayPublicKey;

    private Integer sortOrder;
}
