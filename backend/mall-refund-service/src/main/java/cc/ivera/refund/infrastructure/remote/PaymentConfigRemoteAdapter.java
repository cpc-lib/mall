package cc.ivera.refund.infrastructure.remote;

import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.refund.infrastructure.remote.client.PaymentServiceFeignClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 支付配置远程适配器：退款服务验签微信退款回调时通过支付服务内部契约读取渠道配置。
 */
@Component
public class PaymentConfigRemoteAdapter extends AbstractRefundRemoteAdapter implements PaymentConfigGateway {

    private final PaymentServiceFeignClient client;

    public PaymentConfigRemoteAdapter(PaymentServiceFeignClient client) {
        this.client = client;
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
