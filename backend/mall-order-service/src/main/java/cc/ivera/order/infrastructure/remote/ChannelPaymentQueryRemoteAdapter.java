package cc.ivera.order.infrastructure.remote;

import cc.ivera.order.infrastructure.remote.client.PaymentServiceClient;
import cc.ivera.payment.application.ChannelPaymentQueryApi;
import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.payment.application.dto.InternalChannelRequest;
import org.springframework.stereotype.Component;

/**
 * 渠道查单远程适配器：查单与本地支付状态同步只在支付服务内执行。
 */
@Component
public class ChannelPaymentQueryRemoteAdapter implements ChannelPaymentQueryApi {
    private static final String SERVICE = "支付服务";

    private final PaymentServiceClient client;

    public ChannelPaymentQueryRemoteAdapter(PaymentServiceClient client) {
        this.client = client;
    }

    @Override
    public ChannelOrderQueryVO query(String payType, String orderNo) {
        InternalChannelRequest request = new InternalChannelRequest();
        request.setPayType(payType);
        request.setOrderNo(orderNo);
        return RemoteResponseUnwrapper.unwrap(client.queryChannelOrder(request), SERVICE);
    }
}
