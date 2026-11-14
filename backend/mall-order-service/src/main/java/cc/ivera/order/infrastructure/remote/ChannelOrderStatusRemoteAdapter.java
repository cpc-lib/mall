package cc.ivera.order.infrastructure.remote;

import cc.ivera.order.infrastructure.remote.client.PaymentServiceClient;
import cc.ivera.payment.application.ChannelOrderStatusApi;
import cc.ivera.payment.application.dto.InternalChannelRequest;
import org.springframework.stereotype.Component;

/**
 * 渠道关单核对远程适配器：渠道订单状态查询与本地状态推进只在支付服务内执行。
 */
@Component
public class ChannelOrderStatusRemoteAdapter implements ChannelOrderStatusApi {
    private static final String SERVICE = "支付服务";

    private final PaymentServiceClient client;

    public ChannelOrderStatusRemoteAdapter(PaymentServiceClient client) {
        this.client = client;
    }

    @Override
    public void checkOrderStatus(String payType, String orderNo) {
        InternalChannelRequest request = new InternalChannelRequest();
        request.setPayType(payType);
        request.setOrderNo(orderNo);
        RemoteResponseUnwrapper.unwrap(client.checkChannelOrderStatus(request), SERVICE);
    }
}
