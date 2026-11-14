package cc.ivera.payment.infrastructure.remote;

import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 支付配置远程适配器：支付应用/渠道配置只由支付服务加载与缓存，BFF 通过契约读取。
 */
@Component
public class PaymentConfigRemoteAdapter extends AbstractPaymentRemoteAdapter implements PaymentConfigGateway {

    public PaymentConfigRemoteAdapter(PaymentServiceClient client) {
        super(client);
    }

    @Override
    public void reloadConfigs() {
        unwrap(client.reloadConfigs());
    }

    @Override
    public PaymentAppConfig getAppConfig(Long appId) {
        return unwrap(client.getAppConfig(appId));
    }

    @Override
    public PaymentAppConfig getRequiredAppConfig(Long appId) {
        return unwrap(client.getRequiredAppConfig(appId));
    }

    @Override
    public PaymentAppConfig getAppConfigByCode(String appCode) {
        return unwrap(client.getAppConfigByCode(appCode));
    }

    @Override
    public PaymentAppConfig getDefaultAppConfigByChannelCode(String channelCode) {
        return unwrap(client.getDefaultAppConfigByChannelCode(channelCode));
    }

    @Override
    public PaymentAppConfig getRequiredDefaultAppConfigByChannelCode(String channelCode) {
        return unwrap(client.getRequiredDefaultAppConfigByChannelCode(channelCode));
    }

    @Override
    public List<PaymentAppConfig> listAppConfigsByChannelCode(String channelCode) {
        return unwrap(client.listAppConfigsByChannelCode(channelCode));
    }

    @Override
    public Map<Long, PaymentAppConfig> getAllAppConfigs() {
        return unwrap(client.getAllAppConfigs());
    }
}
