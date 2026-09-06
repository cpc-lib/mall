package cc.ivera.controller.support;

import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.config.WxPayConfig;
import cc.ivera.exception.BizException;
import cc.ivera.util.HttpUtils;
import cc.ivera.util.JsonUtils;
import cc.ivera.util.WechatPay2ValidatorForRequest;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.ScheduledUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@Slf4j
public class WxPayNotifyHandler {

    private static final String NOTIFY_IDEMPOTENT_KEY_PREFIX = "payment:wx:notify:processed:";

    private static final long NOTIFY_IDEMPOTENT_EXPIRE_HOURS = 24L;

    private final Verifier defaultVerifier;

    private final WxPayConfig wxPayConfig;

    private final PaymentConfigLoader paymentConfigLoader;

    private final StringRedisTemplate stringRedisTemplate;

    private final Map<String, Verifier> verifierCache = new ConcurrentHashMap<>();

    public WxPayNotifyHandler(Verifier verifier,
                              WxPayConfig wxPayConfig,
                              PaymentConfigLoader paymentConfigLoader,
                              StringRedisTemplate stringRedisTemplate) {
        this.defaultVerifier = verifier;
        this.wxPayConfig = wxPayConfig;
        this.paymentConfigLoader = paymentConfigLoader;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String handle(HttpServletRequest request,
                         HttpServletResponse response,
                         Consumer<Map<String, Object>> processor,
                         String errorMessage) {
        try {
            String body = HttpUtils.readData(request);
            Map<String, Object> bodyMap = JsonUtils.toObjectMap(body);
            if (bodyMap == null) {
                throw new IllegalArgumentException("微信通知报文不能为空");
            }

            String requestId = (String) bodyMap.get("id");
            log.info("微信支付通知id ===> {}", requestId);

            if (!tryAcquireNotifyLock(requestId)) {
                log.info("微信通知已处理或正在处理中，直接返回成功，requestId={}", requestId);
                return successNotifyResponse(response);
            }

            try {
                if (!validateRequestWithAnyVerifier(request, requestId, body)) {
                    log.error("通知验签失败");
                    releaseNotifyLock(requestId);
                    return errorNotifyResponse(response, "通知验签失败");
                }
                log.info("通知验签成功");

                processor.accept(bodyMap);
                markNotifyProcessed(requestId);
                return successNotifyResponse(response);
            } catch (Exception e) {
                releaseNotifyLock(requestId);
                throw e;
            }
        } catch (Exception e) {
            log.error(errorMessage, e);
            return errorNotifyResponse(response, "失败");
        }
    }

    private boolean validateRequestWithAnyVerifier(HttpServletRequest request, String requestId, String body) {
        for (Verifier verifier : resolveCandidateVerifiers()) {
            try {
                WechatPay2ValidatorForRequest validator = new WechatPay2ValidatorForRequest(verifier, requestId, body);
                if (validator.validate(request)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("微信通知验签候选配置未通过", e);
            }
        }
        return false;
    }

    private List<Verifier> resolveCandidateVerifiers() {
        List<Verifier> verifiers = new ArrayList<>();
        verifiers.add(defaultVerifier);
        for (PaymentAppConfig config : paymentConfigLoader.listAppConfigsByChannelCode(PaymentConfigLoader.CHANNEL_WXPAY)) {
            if (isValidWxVerifierConfig(config)) {
                verifiers.add(verifierCache.computeIfAbsent(buildVerifierCacheKey(config), ignored -> buildVerifier(config)));
            }
        }
        return verifiers;
    }

    private boolean isValidWxVerifierConfig(PaymentAppConfig config) {
        return config != null
                && StringUtils.hasText(config.getMchId())
                && StringUtils.hasText(config.getMchSerialNo())
                && StringUtils.hasText(config.getPrivateKeyPath())
                && StringUtils.hasText(config.getApiV3Key());
    }

    private Verifier buildVerifier(PaymentAppConfig config) {
        PrivateKey privateKey = wxPayConfig.getPrivateKey(config.getPrivateKeyPath());
        PrivateKeySigner privateKeySigner = new PrivateKeySigner(config.getMchSerialNo(), privateKey);
        WechatPay2Credentials credentials = new WechatPay2Credentials(config.getMchId(), privateKeySigner);
        return new ScheduledUpdateCertificatesVerifier(credentials, config.getApiV3Key().getBytes(StandardCharsets.UTF_8));
    }

    private String buildVerifierCacheKey(PaymentAppConfig config) {
        return required(config.getMchId(), "微信商户号未配置") + ":"
                + required(config.getMchSerialNo(), "微信商户API证书序列号未配置") + ":"
                + required(config.getPrivateKeyPath(), "微信私钥文件路径未配置") + ":"
                + required(config.getApiV3Key(), "微信APIv3密钥未配置").hashCode();
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private boolean tryAcquireNotifyLock(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return true;
        }
        String key = NOTIFY_IDEMPOTENT_KEY_PREFIX + requestId;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "processing", NOTIFY_IDEMPOTENT_EXPIRE_HOURS, TimeUnit.HOURS);
        return Boolean.TRUE.equals(success);
    }

    private void releaseNotifyLock(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String key = NOTIFY_IDEMPOTENT_KEY_PREFIX + requestId;
        String currentValue = stringRedisTemplate.opsForValue().get(key);
        if ("processing".equals(currentValue)) {
            stringRedisTemplate.delete(key);
        }
    }

    private void markNotifyProcessed(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String key = NOTIFY_IDEMPOTENT_KEY_PREFIX + requestId;
        stringRedisTemplate.opsForValue().set(key, "processed", NOTIFY_IDEMPOTENT_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    private String successNotifyResponse(HttpServletResponse response) {
        return notifyResponse(response, 200, "SUCCESS", "成功");
    }

    private String errorNotifyResponse(HttpServletResponse response, String message) {
        return notifyResponse(response, 500, "ERROR", message);
    }

    private String notifyResponse(HttpServletResponse response, int status, String code, String message) {
        response.setStatus(status);
        Map<String, String> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);
        return JsonUtils.toJson(map);
    }
}
