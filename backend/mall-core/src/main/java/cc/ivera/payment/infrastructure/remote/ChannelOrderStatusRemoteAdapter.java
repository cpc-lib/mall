package cc.ivera.payment.infrastructure.remote;

import cc.ivera.payment.application.ChannelOrderStatusApi;
import cc.ivera.payment.application.dto.InternalChannelRequest;
import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

/**
 * 渠道关单核对远程适配器：渠道订单状态查询与本地状态推进只在支付服务内执行。
 */
@Component
public class ChannelOrderStatusRemoteAdapter extends AbstractPaymentRemoteAdapter implements ChannelOrderStatusApi {

    public ChannelOrderStatusRemoteAdapter(PaymentServiceClient client) {
        super(client);
    }

    @Override
    public void checkOrderStatus(String payType, String orderNo) {
        InternalChannelRequest request = new InternalChannelRequest();
        request.setPayType(payType);
        request.setOrderNo(orderNo);
        unwrap(client.checkChannelOrderStatus(request));
    }
}
