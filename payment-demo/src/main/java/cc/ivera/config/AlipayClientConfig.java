package cc.ivera.config;

import com.alipay.api.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
//加载配置文件
@PropertySource("classpath:alipay-sandbox.properties")
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayClientConfig {

    private final AlipayProperties alipayProperties;

    public AlipayClientConfig(
        AlipayProperties alipayProperties
    ) {
        this.alipayProperties = alipayProperties;
    }

    @Bean
    public AlipayClient alipayClient() throws AlipayApiException {
        AlipayConfig alipayConfig = new AlipayConfig();

        //设置网关地址
        alipayConfig.setServerUrl(alipayProperties.getGatewayUrl());
        //设置应用Id
        alipayConfig.setAppId(alipayProperties.getAppId());
        //设置应用私钥
        alipayConfig.setPrivateKey(alipayProperties.getMerchantPrivateKey());
        //设置请求格式，固定值json
        alipayConfig.setFormat(AlipayConstants.FORMAT_JSON);
        //设置字符集
        alipayConfig.setCharset(AlipayConstants.CHARSET_UTF8);
        //设置支付宝公钥
        alipayConfig.setAlipayPublicKey(alipayProperties.getAlipayPublicKey());
        //设置签名类型
        alipayConfig.setSignType(AlipayConstants.SIGN_TYPE_RSA2);
        //构造client
        return new DefaultAlipayClient(alipayConfig);
    }
}
