package cc.ivera.payment.infrastructure.config;

import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentApp;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.domain.repository.PaymentAppRepository;
import cc.ivera.payment.domain.repository.PaymentChannelRepository;
import cc.ivera.shared.domain.exception.BizException;
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
 * 支付配置加载器（基础设施适配器）：从数据库加载支付渠道和支付应用配置。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>渠道表保存渠道级公共参数（domain/gatewayUrl/contentKey/notifyUrl/returnUrl）与商户凭证（appid、商户号、证书序列号、私钥内容、API密钥）。</li>
 *   <li>应用表只保存应用业务信息（名称/编码/状态/描述/排序/所属渠道），不保存商户密钥。</li>
 *   <li>运行期只读取 ENABLED 配置，管理端变更后会主动 reload。</li>
 * </ul>
 */
@Component
@Slf4j
public class PaymentConfigLoader implements PaymentConfigGateway {

    private final PaymentAppRepository paymentAppRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final ObjectMapper objectMapper;

    /**
     * 缓存的应用配置 - key: appId
     */
    private final Map<Long, PaymentAppConfig> appConfigCache = new ConcurrentHashMap<>();

    /**
     * 缓存的渠道配置 - key: channelCode
     */
    private final Map<String, PaymentChannel> channelCache = new ConcurrentHashMap<>();

    public PaymentConfigLoader(PaymentAppRepository paymentAppRepository,
                               PaymentChannelRepository paymentChannelRepository,
                               ObjectMapper objectMapper) {
        this.paymentAppRepository = paymentAppRepository;
        this.paymentChannelRepository = paymentChannelRepository;
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
    @Override
    public synchronized void reloadConfigs() {
        log.info("开始重新加载支付配置...");
        loadChannels();
        loadApps();
        log.info("支付配置加载完成，渠道数: {}, 应用数: {}", channelCache.size(), appConfigCache.size());
    }

    private void loadChannels() {
        channelCache.clear();
        List<PaymentChannel> channels = paymentChannelRepository.listEnabled();
        for (PaymentChannel channel : channels) {
            channelCache.put(channel.getChannelCode(), channel);
            log.debug("加载支付渠道: {} ({})", channel.getChannelName(), channel.getChannelCode());
        }
    }

    private void loadApps() {
        appConfigCache.clear();
        List<PaymentApp> apps = paymentAppRepository.listEnabled();
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

        PaymentChannel channel = paymentChannelRepository.findById(app.getChannelId());
        if (channel == null) {
            throw new BizException("支付应用未关联有效渠道，appId=" + app.getId());
        }
        config.setChannelCode(channel.getChannelCode());

        Map<String, String> channelConfig = parseJsonObject(channel.getConfigParams(), "支付渠道配置JSON格式错误，channelId=" + channel.getId());

        // 渠道公共参数（domain/gatewayUrl/contentKey/notifyUrl/returnUrl）
        config.setDomain(channelConfig.get("domain"));
        config.setGatewayUrl(channelConfig.get("gatewayUrl"));
        config.setContentKey(channelConfig.get("contentKey"));
        config.setNotifyUrl(channelConfig.get("notifyUrl"));
        config.setReturnUrl(channelConfig.get("returnUrl"));

        // 微信商户信息（来自渠道表）
        config.setAppid(channel.getAppid());
        config.setMchId(channel.getMchId());
        config.setMchSerialNo(channel.getMchSerialNo());
        config.setPrivateKey(channel.getPrivateKey());
        config.setApiV3Key(channel.getApiV3Key());
        config.setPartnerKey(channel.getPartnerKey());

        // 支付宝商户信息（来自渠道表）
        config.setAlipayAppId(channel.getAlipayAppId());
        config.setSellerId(channel.getSellerId());
        config.setMerchantPrivateKey(channel.getMerchantPrivateKey());
        config.setAlipayPublicKey(channel.getAlipayPublicKey());
        config.setAlipayNotifyUrl(channelConfig.get("notifyUrl"));

        return config;
    }

    @Override
    public PaymentAppConfig getAppConfig(Long appId) {
        if (appId == null) {
            return null;
        }
        PaymentAppConfig config = appConfigCache.get(appId);
        if (config != null) {
            return config;
        }
        PaymentApp app = paymentAppRepository.findById(appId);
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

    @Override
    public PaymentAppConfig getRequiredAppConfig(Long appId) {
        PaymentAppConfig config = getAppConfig(appId);
        if (config == null) {
            throw new BizException("支付应用配置不存在或未启用");
        }
        return config;
    }

    @Override
    public PaymentAppConfig getAppConfigByCode(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return null;
        }
        return appConfigCache.values().stream()
            .filter(config -> appCode.equals(config.getAppCode()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public PaymentAppConfig getDefaultAppConfigByChannelCode(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            return null;
        }
        return appConfigCache.values().stream()
            .filter(config -> channelCode.equals(config.getChannelCode()))
            .sorted((left, right) -> {
                PaymentApp leftApp = paymentAppRepository.findById(left.getAppId());
                PaymentApp rightApp = paymentAppRepository.findById(right.getAppId());
                Integer leftOrder = leftApp == null ? 0 : leftApp.getSortOrder();
                Integer rightOrder = rightApp == null ? 0 : rightApp.getSortOrder();
                return Integer.compare(leftOrder == null ? 0 : leftOrder, rightOrder == null ? 0 : rightOrder);
            })
            .findFirst()
            .orElse(null);
    }

    @Override
    public PaymentAppConfig getRequiredDefaultAppConfigByChannelCode(String channelCode) {
        PaymentAppConfig config = getDefaultAppConfigByChannelCode(channelCode);
        if (config == null) {
            throw new BizException("未配置启用的支付应用，channelCode=" + channelCode);
        }
        return config;
    }

    @Override
    public List<PaymentAppConfig> listAppConfigsByChannelCode(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            return new ArrayList<>();
        }
        return appConfigCache.values().stream()
            .filter(config -> channelCode.equals(config.getChannelCode()))
            .collect(Collectors.toList());
    }

    @Override
    public Map<Long, PaymentAppConfig> getAllAppConfigs() {
        return new HashMap<>(appConfigCache);
    }

    private Map<String, String> parseJsonObject(String json, String errorMessage) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                result.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
            return result;
        } catch (Exception e) {
            throw new BizException(errorMessage, e);
        }
    }

    private String rootCauseMessage(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause == null ? e.getMessage() : cause.getMessage();
    }
}
