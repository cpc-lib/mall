package cc.ivera.config;

import cc.ivera.entity.PaymentApp;
import cc.ivera.entity.PaymentChannel;
import cc.ivera.exception.BizException;
import cc.ivera.service.PaymentAppService;
import cc.ivera.service.PaymentChannelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 支付配置加载器 - 从数据库加载支付渠道和支付应用配置。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>渠道配置表保存渠道级公共参数，例如微信/支付宝网关域名。</li>
 *   <li>应用配置表保存商户/应用维度参数，例如 appId、mchId、密钥、通知地址。</li>
 *   <li>运行期只读取 ENABLED 配置，管理端变更后会主动 reload。</li>
 * </ul>
 */
@Component
@Slf4j
public class PaymentConfigLoader {

    public static final String CHANNEL_WXPAY = "WXPAY";
    public static final String CHANNEL_ALIPAY = "ALIPAY";

    private final PaymentAppService paymentAppService;
    private final PaymentChannelService paymentChannelService;
    private final ObjectMapper objectMapper;

    /**
     * 缓存的应用配置 - key: appId
     */
    private final Map<Long, PaymentAppConfig> appConfigCache = new ConcurrentHashMap<>();

    /**
     * 缓存的渠道配置 - key: channelCode
     */
    private final Map<String, PaymentChannel> channelCache = new ConcurrentHashMap<>();

    public PaymentConfigLoader(PaymentAppService paymentAppService,
                               PaymentChannelService paymentChannelService,
                               ObjectMapper objectMapper) {
        this.paymentAppService = paymentAppService;
        this.paymentChannelService = paymentChannelService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            reloadConfigs();
        } catch (DataAccessException e) {
            channelCache.clear();
            appConfigCache.clear();
            log.warn("Payment config tables are not available during startup; continuing with empty DB payment config cache. Run full DM8 initialization SQL before using DB-backed payment config. cause={}", rootCauseMessage(e));
        }
    }

    /**
     * 重新加载所有启用配置。
     */
    public synchronized void reloadConfigs() {
        log.info("开始重新加载支付配置...");
        loadChannels();
        loadApps();
        log.info("支付配置加载完成，渠道数: {}, 应用数: {}", channelCache.size(), appConfigCache.size());
    }

    private void loadChannels() {
        channelCache.clear();
        List<PaymentChannel> channels = paymentChannelService.listEnabledChannels();
        for (PaymentChannel channel : channels) {
            channelCache.put(channel.getChannelCode(), channel);
            log.debug("加载支付渠道: {} ({})", channel.getChannelName(), channel.getChannelCode());
        }
    }

    private void loadApps() {
        appConfigCache.clear();
        List<PaymentApp> apps = paymentAppService.listEnabledApps();
        for (PaymentApp app : apps) {
            try {
                PaymentAppConfig config = convertToConfig(app);
                if (StringUtils.hasText(config.getChannelCode()) && channelCache.containsKey(config.getChannelCode())) {
                    appConfigCache.put(app.getId(), config);
                    log.debug("加载支付应用: {} ({})", app.getAppName(), app.getAppCode());
                }
            } catch (Exception e) {
                log.error("加载支付应用配置失败，appId={}, appCode={}", app.getId(), app.getAppCode(), e);
            }
        }
    }

    private PaymentAppConfig convertToConfig(PaymentApp app) {
        PaymentAppConfig config = new PaymentAppConfig();
        config.setAppId(app.getId());
        config.setAppName(app.getAppName());
        config.setAppCode(app.getAppCode());
        config.setChannelId(app.getChannelId());

        PaymentChannel channel = paymentChannelService.getById(app.getChannelId());
        if (channel == null) {
            throw new BizException("支付应用未关联有效渠道，appId=" + app.getId());
        }
        config.setChannelCode(channel.getChannelCode());

        Map<String, String> channelConfig = parseJsonObject(channel.getConfigParams(), "支付渠道配置JSON格式错误，channelId=" + channel.getId());
        Map<String, String> appConfig = parseJsonObject(app.getAppConfig(), "支付应用配置JSON格式错误，appId=" + app.getId());

        // 渠道公共参数
        config.setDomain(channelConfig.get("domain"));
        config.setGatewayUrl(channelConfig.get("gatewayUrl"));
        config.setContentKey(channelConfig.get("contentKey"));

        // 微信支付应用参数
        config.setAppid(appConfig.get("appid"));
        config.setMchId(appConfig.get("mchId"));
        config.setMchSerialNo(appConfig.get("mchSerialNo"));
        config.setPrivateKeyPath(appConfig.get("privateKeyPath"));
        config.setApiV3Key(firstNonBlank(appConfig.get("apiV3Key"), channelConfig.get("apiV3Key")));
        config.setPartnerKey(firstNonBlank(appConfig.get("partnerKey"), channelConfig.get("partnerKey")));
        config.setNotifyUrl(firstNonBlank(appConfig.get("notifyUrl"), channelConfig.get("notifyUrl")));

        // 支付宝应用参数
        config.setAlipayAppId(firstNonBlank(appConfig.get("appId"), appConfig.get("alipayAppId")));
        config.setSellerId(appConfig.get("sellerId"));
        config.setMerchantPrivateKey(appConfig.get("merchantPrivateKey"));
        config.setAlipayPublicKey(appConfig.get("alipayPublicKey"));
        config.setReturnUrl(appConfig.get("returnUrl"));
        config.setAlipayNotifyUrl(firstNonBlank(appConfig.get("notifyUrl"), channelConfig.get("notifyUrl")));

        return config;
    }

    public PaymentAppConfig getAppConfig(Long appId) {
        if (appId == null) {
            return null;
        }
        PaymentAppConfig config = appConfigCache.get(appId);
        if (config != null) {
            return config;
        }
        PaymentApp app = paymentAppService.getById(appId);
        if (app == null || !"ENABLED".equals(app.getAppStatus())) {
            return null;
        }
        config = convertToConfig(app);
        if (!channelCache.containsKey(config.getChannelCode())) {
            return null;
        }
        appConfigCache.put(appId, config);
        return config;
    }

    public PaymentAppConfig getRequiredAppConfig(Long appId) {
        PaymentAppConfig config = getAppConfig(appId);
        if (config == null) {
            throw new BizException("支付应用配置不存在或未启用");
        }
        return config;
    }

    public PaymentChannel getChannel(String channelCode) {
        return channelCache.get(channelCode);
    }

    public PaymentAppConfig getAppConfigByCode(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return null;
        }
        return appConfigCache.values().stream()
                .filter(config -> appCode.equals(config.getAppCode()))
                .findFirst()
                .orElse(null);
    }

    public PaymentAppConfig getDefaultAppConfigByChannelCode(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            return null;
        }
        return appConfigCache.values().stream()
                .filter(config -> channelCode.equals(config.getChannelCode()))
                .sorted((left, right) -> {
                    PaymentApp leftApp = paymentAppService.getById(left.getAppId());
                    PaymentApp rightApp = paymentAppService.getById(right.getAppId());
                    Integer leftOrder = leftApp == null ? 0 : leftApp.getSortOrder();
                    Integer rightOrder = rightApp == null ? 0 : rightApp.getSortOrder();
                    return Integer.compare(leftOrder == null ? 0 : leftOrder, rightOrder == null ? 0 : rightOrder);
                })
                .findFirst()
                .orElse(null);
    }

    public PaymentAppConfig getRequiredDefaultAppConfigByChannelCode(String channelCode) {
        PaymentAppConfig config = getDefaultAppConfigByChannelCode(channelCode);
        if (config == null) {
            throw new BizException("未配置启用的支付应用，channelCode=" + channelCode);
        }
        return config;
    }

    public List<PaymentAppConfig> listAppConfigsByChannelCode(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            return new ArrayList<>();
        }
        return appConfigCache.values().stream()
                .filter(config -> channelCode.equals(config.getChannelCode()))
                .collect(Collectors.toList());
    }

    public Map<Long, PaymentAppConfig> getAllAppConfigs() {
        return new HashMap<>(appConfigCache);
    }

    private Map<String, String> parseJsonObject(String json, String errorMessage) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                result.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
            return result;
        } catch (Exception e) {
            throw new BizException(errorMessage, e);
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String rootCauseMessage(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause == null ? e.getMessage() : cause.getMessage();
    }
}
