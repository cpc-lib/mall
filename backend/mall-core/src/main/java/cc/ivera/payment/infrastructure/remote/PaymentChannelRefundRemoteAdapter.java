package cc.ivera.payment.infrastructure.remote;

import cc.ivera.api.payment.PaymentChannelRefundApi;
import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;
import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 渠道退款远程适配器：退款上下文只通过契约触达支付服务，不引用支付渠道实现。
 */
@Component
public class PaymentChannelRefundRemoteAdapter extends AbstractPaymentRemoteAdapter
    implements PaymentChannelRefundApi {

    public PaymentChannelRefundRemoteAdapter(PaymentServiceClient client) {
        super(client);
    }

    @Override
    public ChannelRefundResult executeRefund(ChannelRefundCommand command) {
        return unwrap(client.executeRefund(command));
    }

    @Override
    public ChannelRefundStatus queryRefund(ChannelRefundQuery query) {
        return unwrap(client.queryRefund(query));
    }

    @Override
    public ChannelRefundSyncResult queryRefundStatusForSync(ChannelRefundQuery query) {
        return unwrap(client.queryRefundStatusForSync(query));
    }

    @Override
    public List<ChannelRefundSyncResult> queryOrderRefundsForSync(String orderNo, String channel) {
        return unwrap(client.queryOrderRefundsForSync(orderNo, channel));
    }

    @Override
    public ChannelRefundSyncResult processRefundNotify(Map<String, Object> bodyMap) {
        return unwrap(client.processRefundNotify(bodyMap));
    }
}
