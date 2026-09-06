package cc.ivera.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /**
     * 应用ID
     */
    private String appId;

    /**
     * 商户PID
     */
    private String sellerId;

    /**
     * 支付宝网关地址
     */
    private String gatewayUrl;

    /**
     * 应用私钥
     */
    private String merchantPrivateKey;

    /**
     * 支付宝公钥
     */
    private String alipayPublicKey;

    /**
     * 接口内容加密密钥
     */
    private String contentKey;

    /**
     * 支付完成后的页面跳转地址
     */
    private String returnUrl;

    /**
     * 支付宝异步通知地址
     */
    private String notifyUrl;
}
