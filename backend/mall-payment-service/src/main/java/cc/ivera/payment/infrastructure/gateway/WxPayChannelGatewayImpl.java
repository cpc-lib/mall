package cc.ivera.payment.infrastructure.gateway;

import cc.ivera.payment.domain.gateway.WxPayChannelGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.infrastructure.util.HttpClientUtils;
import cc.ivera.payment.infrastructure.util.WxPayPrivateKeyUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.text.ParseException;
import java.util.Map;

/**
 * 微信支付渠道端口实现：组合 V3 签名 HTTP 客户端、通知解密、V2 XML 客户端与商户私钥加载。
 */
@Component
public class WxPayChannelGatewayImpl implements WxPayChannelGateway {

    private final WxPayHttpClient wxPayHttpClient;
    private final WxPayNotificationDecoder wxPayNotificationDecoder;

    public WxPayChannelGatewayImpl(WxPayHttpClient wxPayHttpClient,
                                   WxPayNotificationDecoder wxPayNotificationDecoder) {
        this.wxPayHttpClient = wxPayHttpClient;
        this.wxPayNotificationDecoder = wxPayNotificationDecoder;
    }

    @Override
    public String get(PaymentAppConfig config, String url, String failureMessage) throws IOException {
        return wxPayHttpClient.get(config, url, failureMessage);
    }

    @Override
    public String getNoSign(PaymentAppConfig config, String url, String failureMessage) throws IOException {
        return wxPayHttpClient.getNoSign(config, url, failureMessage);
    }

    @Override
    public String postJson(PaymentAppConfig config, String url, String jsonParams, String failureMessage) throws IOException {
        return wxPayHttpClient.postJson(config, url, jsonParams, failureMessage);
    }

    @Override
    public WxPayResponse postJsonForResponse(PaymentAppConfig config, String url, String jsonParams) throws IOException {
        WxPayHttpClient.WxPayHttpResponse response = wxPayHttpClient.postJsonForResponse(config, url, jsonParams);
        return new WxPayResponse(response.getStatusCode(), response.getBody());
    }

    @Override
    public String decryptNotification(Map<String, Object> bodyMap) throws GeneralSecurityException {
        return wxPayNotificationDecoder.decryptResource(bodyMap);
    }

    @Override
    public String postXml(String url, String xmlParams) throws IOException {
        HttpClientUtils client = new HttpClientUtils(url);
        client.setXmlParam(xmlParams);
        client.setHttps(true);
        client.post();
        try {
            return client.getContent();
        } catch (ParseException e) {
            throw new IOException("解析微信支付响应失败", e);
        }
    }

    @Override
    public PrivateKey loadPrivateKey(String pemContent) {
        return WxPayPrivateKeyUtil.load(pemContent);
    }
}
