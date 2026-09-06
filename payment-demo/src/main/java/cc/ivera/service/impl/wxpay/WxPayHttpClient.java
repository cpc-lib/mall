package cc.ivera.service.impl.wxpay;

import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.WxPayConfig;
import cc.ivera.exception.BizException;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.ScheduledUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WxPayHttpClient {

    private final CloseableHttpClient wxPayClient;

    private final CloseableHttpClient wxPayNoSignClient;

    private final WxPayConfig wxPayConfig;

    /**
     * 多支付应用场景下，按商户号/证书序列号/私钥/APIv3密钥维度缓存真实微信 V3 HttpClient。
     */
    private final Map<String, CloseableHttpClient> signedClientCache = new ConcurrentHashMap<>();

    private final Map<String, CloseableHttpClient> noSignClientCache = new ConcurrentHashMap<>();

    public WxPayHttpClient(
        @Qualifier("wxPayClient") CloseableHttpClient wxPayClient,
        @Qualifier("wxPayNoSignClient") CloseableHttpClient wxPayNoSignClient,
        WxPayConfig wxPayConfig
    ) {
        this.wxPayClient = wxPayClient;
        this.wxPayNoSignClient = wxPayNoSignClient;
        this.wxPayConfig = wxPayConfig;
    }

    public String get(String url, String failureMessage) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        return executeForSuccess(wxPayClient, httpGet, failureMessage);
    }

    public String get(PaymentAppConfig payConfig, String url, String failureMessage) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        return executeForSuccess(getSignedClient(payConfig), httpGet, failureMessage);
    }

    public String getNoSign(String url, String failureMessage) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        return executeForSuccess(wxPayNoSignClient, httpGet, failureMessage);
    }

    public String getNoSign(PaymentAppConfig payConfig, String url, String failureMessage) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        return executeForSuccess(getNoSignClient(payConfig), httpGet, failureMessage);
    }

    public String postJson(String url, String jsonParams, String failureMessage) throws IOException {
        WxPayHttpResponse response = postJsonForResponse(url, jsonParams);
        if (!response.isSuccessful()) {
            throw new IOException(failureMessage + ", 响应码 = " + response.getStatusCode() + ", 返回结果 = " + response.getBody());
        }
        return response.getBody();
    }

    public String postJson(PaymentAppConfig payConfig, String url, String jsonParams, String failureMessage) throws IOException {
        WxPayHttpResponse response = postJsonForResponse(payConfig, url, jsonParams);
        if (!response.isSuccessful()) {
            throw new IOException(failureMessage + ", 响应码 = " + response.getStatusCode() + ", 返回结果 = " + response.getBody());
        }
        return response.getBody();
    }

    public WxPayHttpResponse postJsonForResponse(String url, String jsonParams) throws IOException {
        HttpPost httpPost = buildJsonPost(url, jsonParams);
        return execute(wxPayClient, httpPost);
    }

    public WxPayHttpResponse postJsonForResponse(PaymentAppConfig payConfig, String url, String jsonParams) throws IOException {
        HttpPost httpPost = buildJsonPost(url, jsonParams);
        return execute(getSignedClient(payConfig), httpPost);
    }

    private HttpPost buildJsonPost(String url, String jsonParams) {
        HttpPost httpPost = new HttpPost(url);
        StringEntity entity = new StringEntity(jsonParams, StandardCharsets.UTF_8);
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        return httpPost;
    }

    private CloseableHttpClient getSignedClient(PaymentAppConfig payConfig) {
        String key = buildClientCacheKey(payConfig);
        return signedClientCache.computeIfAbsent(key, ignored -> buildSignedClient(payConfig));
    }

    private CloseableHttpClient getNoSignClient(PaymentAppConfig payConfig) {
        String key = buildClientCacheKey(payConfig);
        return noSignClientCache.computeIfAbsent(key, ignored -> buildNoSignClient(payConfig));
    }

    private CloseableHttpClient buildSignedClient(PaymentAppConfig payConfig) {
        PrivateKey privateKey = wxPayConfig.getPrivateKey(required(payConfig.getPrivateKeyPath(), "微信私钥文件路径未配置"));
        PrivateKeySigner privateKeySigner = new PrivateKeySigner(required(payConfig.getMchSerialNo(), "微信商户API证书序列号未配置"), privateKey);
        WechatPay2Credentials credentials = new WechatPay2Credentials(required(payConfig.getMchId(), "微信商户号未配置"), privateKeySigner);
        ScheduledUpdateCertificatesVerifier verifier = new ScheduledUpdateCertificatesVerifier(
                credentials,
                required(payConfig.getApiV3Key(), "微信APIv3密钥未配置").getBytes(StandardCharsets.UTF_8));
        return WechatPayHttpClientBuilder.create()
                .withMerchant(payConfig.getMchId(), payConfig.getMchSerialNo(), privateKey)
                .withValidator(new WechatPay2Validator(verifier))
                .build();
    }

    private CloseableHttpClient buildNoSignClient(PaymentAppConfig payConfig) {
        PrivateKey privateKey = wxPayConfig.getPrivateKey(required(payConfig.getPrivateKeyPath(), "微信私钥文件路径未配置"));
        return WechatPayHttpClientBuilder.create()
                .withMerchant(required(payConfig.getMchId(), "微信商户号未配置"),
                        required(payConfig.getMchSerialNo(), "微信商户API证书序列号未配置"),
                        privateKey)
                .withValidator(response -> true)
                .build();
    }

    private String buildClientCacheKey(PaymentAppConfig payConfig) {
        return required(payConfig.getMchId(), "微信商户号未配置") + ":"
                + required(payConfig.getMchSerialNo(), "微信商户API证书序列号未配置") + ":"
                + required(payConfig.getPrivateKeyPath(), "微信私钥文件路径未配置") + ":"
                + required(payConfig.getApiV3Key(), "微信APIv3密钥未配置").hashCode();
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String executeForSuccess(CloseableHttpClient client,
                                     HttpUriRequest request,
                                     String failureMessage) throws IOException {
        WxPayHttpResponse response = execute(client, request);
        if (!response.isSuccessful()) {
            throw new IOException(failureMessage + ", 响应码 = " + response.getStatusCode() + ", 返回结果 = " + response.getBody());
        }
        return response.getBody();
    }

    private WxPayHttpResponse execute(CloseableHttpClient client, HttpUriRequest request) throws IOException {
        CloseableHttpResponse response = client.execute(request);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity);
            if (statusCode == 200) {
                log.info("微信支付请求成功, 返回结果 = {}", body);
            } else if (statusCode == 204) {
                log.info("微信支付请求成功");
            } else {
                log.info("微信支付请求失败, 响应码 = {}, 返回结果 = {}", statusCode, body);
            }
            return new WxPayHttpResponse(statusCode, body);
        } finally {
            response.close();
        }
    }

    @Getter
    public static class WxPayHttpResponse {

        private final int statusCode;

        private final String body;

        private WxPayHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public boolean isSuccessful() {
            return statusCode == 200 || statusCode == 204;
        }
    }
}
