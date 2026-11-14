package cc.ivera.payment.domain.gateway;

import cc.ivera.payment.domain.model.PaymentAppConfig;

import java.util.List;
import java.util.Map;

/**
 * 支付配置网关端口：运行期支付应用/渠道配置（DB 加载 + 缓存 + 管理端 reload）。
 * 适配器（基础设施）从 t_payment_app/t_payment_channel 加载，启动期 DB 不可用则清空缓存告警不失败。
 */
public interface PaymentConfigGateway {

    String CHANNEL_WXPAY = "WXPAY";

    String CHANNEL_ALIPAY = "ALIPAY";

    /**
     * 线下/手工收款渠道（管理员标记付款）：无渠道资金，退款时本地结转，不调用渠道退款接口。
     */
    String CHANNEL_OFFLINE = "OFFLINE";

    /**
     * 重新加载所有启用配置（管理端增删改渠道/应用后调用）。
     */
    void reloadConfigs();

    PaymentAppConfig getAppConfig(Long appId);

    PaymentAppConfig getRequiredAppConfig(Long appId);

    PaymentAppConfig getAppConfigByCode(String appCode);

    PaymentAppConfig getDefaultAppConfigByChannelCode(String channelCode);

    PaymentAppConfig getRequiredDefaultAppConfigByChannelCode(String channelCode);

    List<PaymentAppConfig> listAppConfigsByChannelCode(String channelCode);

    Map<Long, PaymentAppConfig> getAllAppConfigs();
}
