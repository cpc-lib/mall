package cc.ivera.payment.application.impl.wxpay;

import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

/**
 * 微信商户私钥解析工具：私钥内容来自渠道表 private_key 列（PEM 文本）。
 * 兼容两种存储形态：真实换行、"\n" 字面量转义。
 */
public final class WxPayPrivateKeyUtil {

    private WxPayPrivateKeyUtil() {
    }

    public static PrivateKey load(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("微信商户私钥内容未配置");
        }
        String normalized = content.replace("\\n", "\n").trim();
        return PemUtil.loadPrivateKey(new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8)));
    }
}
