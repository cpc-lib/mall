package cc.ivera.order.infrastructure.remote;

import cc.ivera.order.infrastructure.remote.client.PaymentServiceClient;
import cc.ivera.payment.application.PaymentSuccessService;
import cc.ivera.payment.application.dto.InternalPaymentSuccessRequest;
import org.springframework.stereotype.Component;

/**
 * 支付成交远程适配器：成交事实只由支付服务写入，订单服务通过契约受理支付成功。
 */
@Component
public class PaymentSuccessRemoteAdapter implements PaymentSuccessService {
    private static final String SERVICE = "支付服务";

    private final PaymentServiceClient client;

    public PaymentSuccessRemoteAdapter(PaymentServiceClient client) {
        this.client = client;
    }

    @Override
    public boolean handlePaymentSuccess(String orderNo, String channel, String channelOrderNo, Integer paidAmount) {
        InternalPaymentSuccessRequest request = new InternalPaymentSuccessRequest();
        request.setOrderNo(orderNo);
        request.setChannel(channel);
        request.setChannelOrderNo(channelOrderNo);
        request.setPaidAmount(paidAmount);
        Boolean result = RemoteResponseUnwrapper.unwrap(client.handlePaymentSuccess(request), SERVICE);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean markOfflinePaid(String orderNo) {
        Boolean result = RemoteResponseUnwrapper.unwrap(client.markOfflinePaid(orderNo), SERVICE);
        return Boolean.TRUE.equals(result);
    }
}
