package cc.ivera.payment.domain.gateway;

import cc.ivera.payment.domain.model.PaymentAppConfig;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Map;

/**
 * 微信支付渠道调用端口：签名/免签 HTTP、通知解密、V2 XML 请求与商户私钥加载。
 * 由 infrastructure.gateway 适配器基于微信 SDK / HTTP 客户端实现。
 */
public interface WxPayChannelGateway {

    String get(PaymentAppConfig config, String url, String failureMessage) throws IOException;

    String getNoSign(PaymentAppConfig config, String url, String failureMessage) throws IOException;

    String postJson(PaymentAppConfig config, String url, String jsonParams, String failureMessage) throws IOException;

    WxPayResponse postJsonForResponse(PaymentAppConfig config, String url, String jsonParams) throws IOException;

    String decryptNotification(Map<String, Object> bodyMap) throws GeneralSecurityException;

    String postXml(String url, String xmlParams) throws IOException;

    PrivateKey loadPrivateKey(String pemContent);

    record WxPayResponse(int statusCode, String body) {

        public boolean isSuccessful() {
            return statusCode == 200 || statusCode == 204;
        }
    }
}
