package cc.ivera.order.infrastructure.remote;

import cc.ivera.order.infrastructure.remote.client.PaymentServiceClient;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 支付配置远程适配器：支付应用/渠道配置只由支付服务加载与缓存。
 */
@Component
public class PaymentConfigRemoteAdapter implements PaymentConfigGateway {
    private static final String SERVICE = "支付服务";

    private final PaymentServiceClient client;

    public PaymentConfigRemoteAdapter(PaymentServiceClient client) {
        this.client = client;
    }

    @Override
    public void reloadConfigs() {
        RemoteResponseUnwrapper.unwrap(client.reloadConfigs(), SERVICE);
    }

    @Override
    public PaymentAppConfig getAppConfig(Long appId) {
        return RemoteResponseUnwrapper.unwrap(client.getAppConfig(appId), SERVICE);
    }

    @Override
    public PaymentAppConfig getRequiredAppConfig(Long appId) {
        return RemoteResponseUnwrapper.unwrap(client.getRequiredAppConfig(appId), SERVICE);
    }

    @Override
    public PaymentAppConfig getAppConfigByCode(String appCode) {
        return RemoteResponseUnwrapper.unwrap(client.getAppConfigByCode(appCode), SERVICE);
    }

    @Override
    public PaymentAppConfig getDefaultAppConfigByChannelCode(String channelCode) {
        return RemoteResponseUnwrapper.unwrap(client.getDefaultAppConfigByChannelCode(channelCode), SERVICE);
    }

    @Override
    public PaymentAppConfig getRequiredDefaultAppConfigByChannelCode(String channelCode) {
        return RemoteResponseUnwrapper.unwrap(client.getRequiredDefaultAppConfigByChannelCode(channelCode), SERVICE);
    }

    @Override
    public List<PaymentAppConfig> listAppConfigsByChannelCode(String channelCode) {
        return RemoteResponseUnwrapper.unwrap(client.listAppConfigsByChannelCode(channelCode), SERVICE);
    }

    @Override
    public Map<Long, PaymentAppConfig> getAllAppConfigs() {
        return RemoteResponseUnwrapper.unwrap(client.getAllAppConfigs(), SERVICE);
    }
}
