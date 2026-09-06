package cc.ivera.service.impl.wxpay;

import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.config.WxPayConfig;
import cc.ivera.util.JsonUtils;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class WxPayNotificationDecoder {

    private final WxPayConfig wxPayConfig;
    private final PaymentConfigLoader paymentConfigLoader;

    public WxPayNotificationDecoder(WxPayConfig wxPayConfig,
                                    PaymentConfigLoader paymentConfigLoader) {
        this.wxPayConfig = wxPayConfig;
        this.paymentConfigLoader = paymentConfigLoader;
    }

    public String decryptResource(Map<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("微信通知密文解密");

        Map<String, Object> resourceMap = JsonUtils.toObjectMap(bodyMap.get("resource"));
        if (resourceMap == null) {
            throw new IllegalArgumentException("微信通知resource不能为空");
        }

        String ciphertext = getRequiredString(resourceMap, "ciphertext");
        String nonce = getRequiredString(resourceMap, "nonce");
        String associatedData = getRequiredString(resourceMap, "associated_data");

        List<String> apiV3Keys = resolveCandidateApiV3Keys();
        GeneralSecurityException lastException = null;
        for (String apiV3Key : apiV3Keys) {
            try {
                AesUtil aesUtil = new AesUtil(apiV3Key.getBytes(StandardCharsets.UTF_8));
                String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8),
                        nonce.getBytes(StandardCharsets.UTF_8),
                        ciphertext);
                log.info("微信通知解密成功");
                return plainText;
            } catch (GeneralSecurityException e) {
                lastException = e;
            }
        }
        throw lastException == null ? new GeneralSecurityException("微信通知解密失败，未配置APIv3密钥") : lastException;
    }

    private List<String> resolveCandidateApiV3Keys() {
        List<String> keys = new ArrayList<>();
        for (PaymentAppConfig config : paymentConfigLoader.listAppConfigsByChannelCode(PaymentConfigLoader.CHANNEL_WXPAY)) {
            if (StringUtils.hasText(config.getApiV3Key()) && !keys.contains(config.getApiV3Key())) {
                keys.add(config.getApiV3Key());
            }
        }
        if (StringUtils.hasText(wxPayConfig.getApiV3Key()) && !keys.contains(wxPayConfig.getApiV3Key())) {
            keys.add(wxPayConfig.getApiV3Key());
        }
        return keys;
    }

    private String getRequiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("微信通知resource." + key + "不能为空");
        }
        return (String) value;
    }
}
