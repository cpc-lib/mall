package cc.ivera.payment.infrastructure.util;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * http请求客户端
 * <p>
 * 设计要点：
 * - 单例连接池 {@link #SHARED_CLIENT}（最多 50 个连接，每个路由最多 10 个），避免每次请求新建 TCP+TLS。
 * - 标准 SSL 验证：使用 JVM 默认 truststore，信任正规 CA（含微信支付、支付宝等公开证书）；
 *   不再禁用 SSL 验证，杜绝 MITM 攻击支付通道的风险。
 * - 统一超时：connectTimeout 5s、socketTimeout 30s、connectionRequestTimeout 2s，
 *   防止对方无响应时无限阻塞。
 */
public class HttpClientUtils {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final int CONNECT_REQUEST_TIMEOUT_MS = 2000;

    /** 单例共享连接池客户端：标准 SSL + 连接池 + 超时 */
    private static final CloseableHttpClient SHARED_CLIENT = createSharedClient();

    private String url;
    private Map<String, String> param;
    private int statusCode;
    private String content;
    private String xmlParam;
    private boolean isHttps;

    public HttpClientUtils(String url, Map<String, String> param) {
        this.url = url;
        this.param = param;
    }

    public HttpClientUtils(String url) {
        this.url = url;
    }

    public boolean isHttps() {
        return isHttps;
    }

    public void setHttps(boolean isHttps) {
        this.isHttps = isHttps;
    }

    public String getXmlParam() {
        return xmlParam;
    }

    public void setXmlParam(String xmlParam) {
        this.xmlParam = xmlParam;
    }

    public void setParameter(Map<String, String> map) {
        param = map;
    }

    public void addParameter(String key, String value) {
        if (param == null) {
            param = new HashMap<>();
        }
        param.put(key, value);
    }

    public void post() throws IOException {
        HttpPost http = new HttpPost(url);
        setEntity(http);
        execute(http);
    }

    public void put() throws IOException {
        HttpPut http = new HttpPut(url);
        setEntity(http);
        execute(http);
    }

    public void get() throws IOException {
        if (param != null) {
            StringBuilder urlBuilder = new StringBuilder(this.url);
            boolean isFirst = true;
            for (String key : param.keySet()) {
                if (isFirst) {
                    urlBuilder.append("?");
                    isFirst = false;
                } else {
                    urlBuilder.append("&");
                }
                urlBuilder.append(key).append("=").append(param.get(key));
            }
            this.url = urlBuilder.toString();
        }
        HttpGet http = new HttpGet(url);
        execute(http);
    }

    /**
     * set http post,put param
     */
    private void setEntity(HttpEntityEnclosingRequestBase http) {
        if (param != null) {
            List<NameValuePair> nvps = new LinkedList<>();
            for (String key : param.keySet()) {
                nvps.add(new BasicNameValuePair(key, param.get(key)));
            }
            http.setEntity(new UrlEncodedFormEntity(nvps, Consts.UTF_8));
        }
        if (xmlParam != null) {
            http.setEntity(new StringEntity(xmlParam, Consts.UTF_8));
        }
    }

    private void execute(HttpRequestBase http) throws IOException {
        try (CloseableHttpResponse response = SHARED_CLIENT.execute(http)) {
            if (response.getStatusLine() != null) {
                statusCode = response.getStatusLine().getStatusCode();
            }
            HttpEntity entity = response.getEntity();
            content = entity == null ? "" : EntityUtils.toString(entity, Consts.UTF_8);
        }
    }

    /**
     * 构建单例连接池客户端：标准 SSL + 连接池 + 统一超时。
     * <p>
     * SSL 使用 JVM 默认 truststore（加载 cacerts），不再信任所有证书。
     * 若生产环境需导入微信支付/支付宝特殊 CA，通过 {@code keytool -importcert} 加到 cacerts 即可。
     */
    private static CloseableHttpClient createSharedClient() {
        RequestConfig defaultConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .setConnectionRequestTimeout(CONNECT_REQUEST_TIMEOUT_MS)
            .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(50);
        cm.setDefaultMaxPerRoute(10);
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(SSLContexts.createDefault());
        return HttpClients.custom()
            .setConnectionManager(cm)
            .setSSLSocketFactory(sslsf)
            .setDefaultRequestConfig(defaultConfig)
            .build();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContent() throws ParseException, IOException {
        return content;
    }

}
