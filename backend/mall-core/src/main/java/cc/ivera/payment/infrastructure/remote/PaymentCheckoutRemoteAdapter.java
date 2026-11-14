package cc.ivera.payment.infrastructure.remote;

import cc.ivera.api.payment.PaymentCheckoutApi;
import cc.ivera.payment.application.dto.WxPayNativeVO;
import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

/**
 * 收银台支付发起远程适配器：渠道下单只在支付服务内执行。
 */
@Component
public class PaymentCheckoutRemoteAdapter extends AbstractPaymentRemoteAdapter implements PaymentCheckoutApi {

    public PaymentCheckoutRemoteAdapter(PaymentServiceClient client) {
        super(client);
    }

    @Override
    public String createAliPayForm(String orderNo) {
        return unwrap(client.createAliPayForm(orderNo));
    }

    @Override
    public WxPayNativeVO createWxPayNative(String orderNo) {
        return unwrap(client.createWxPayNative(orderNo));
    }
}
