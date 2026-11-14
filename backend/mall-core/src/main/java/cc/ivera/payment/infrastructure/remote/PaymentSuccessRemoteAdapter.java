package cc.ivera.payment.infrastructure.remote;

import cc.ivera.payment.application.PaymentSuccessService;
import cc.ivera.payment.application.dto.InternalPaymentSuccessRequest;
import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

/**
 * 支付成交远程适配器：成交事实只由支付服务写入，BFF 通过契约受理支付成功。
 */
@Component
public class PaymentSuccessRemoteAdapter extends AbstractPaymentRemoteAdapter implements PaymentSuccessService {

    public PaymentSuccessRemoteAdapter(PaymentServiceClient client) {
        super(client);
    }

    @Override
    public boolean handlePaymentSuccess(String orderNo, String channel, String channelOrderNo, Integer paidAmount) {
        InternalPaymentSuccessRequest request = new InternalPaymentSuccessRequest();
        request.setOrderNo(orderNo);
        request.setChannel(channel);
        request.setChannelOrderNo(channelOrderNo);
        request.setPaidAmount(paidAmount);
        Boolean result = unwrap(client.handlePaymentSuccess(request));
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean markOfflinePaid(String orderNo) {
        Boolean result = unwrap(client.markOfflinePaid(orderNo));
        return Boolean.TRUE.equals(result);
    }
}
