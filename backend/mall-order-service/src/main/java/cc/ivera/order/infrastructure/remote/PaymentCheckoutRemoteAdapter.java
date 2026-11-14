package cc.ivera.order.infrastructure.remote;

import cc.ivera.api.payment.PaymentCheckoutApi;
import cc.ivera.order.infrastructure.remote.client.PaymentServiceClient;
import cc.ivera.payment.application.dto.WxPayNativeVO;
import org.springframework.stereotype.Component;

/**
 * 收银台支付发起远程适配器：渠道下单只在支付服务内执行。
 */
@Component
public class PaymentCheckoutRemoteAdapter implements PaymentCheckoutApi {
    private static final String SERVICE = "支付服务";

    private final PaymentServiceClient client;

    public PaymentCheckoutRemoteAdapter(PaymentServiceClient client) {
        this.client = client;
    }

    @Override
    public String createAliPayForm(String orderNo) {
        return RemoteResponseUnwrapper.unwrap(client.createAliPayForm(orderNo), SERVICE);
    }

    @Override
    public WxPayNativeVO createWxPayNative(String orderNo) {
        return RemoteResponseUnwrapper.unwrap(client.createWxPayNative(orderNo), SERVICE);
    }
}
