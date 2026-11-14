package cc.ivera.payment.infrastructure.remote;

import cc.ivera.payment.application.ChannelPaymentQueryApi;
import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.payment.application.dto.InternalChannelRequest;
import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

/**
 * 渠道查单远程适配器：查单与本地支付状态同步只在支付服务内执行。
 */
@Component
public class ChannelPaymentQueryRemoteAdapter extends AbstractPaymentRemoteAdapter implements ChannelPaymentQueryApi {

    public ChannelPaymentQueryRemoteAdapter(PaymentServiceClient client) {
        super(client);
    }

    @Override
    public ChannelOrderQueryVO query(String payType, String orderNo) {
        InternalChannelRequest request = new InternalChannelRequest();
        request.setPayType(payType);
        request.setOrderNo(orderNo);
        return unwrap(client.queryChannelOrder(request));
    }
}
