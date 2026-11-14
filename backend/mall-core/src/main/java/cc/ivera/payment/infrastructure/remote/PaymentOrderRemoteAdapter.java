package cc.ivera.payment.infrastructure.remote;

import cc.ivera.api.payment.PaymentOrderCommandApi;
import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.payment.application.dto.InternalOrderNosRequest;
import cc.ivera.payment.application.dto.InternalPaidTimeRangeRequest;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 支付单远程适配器：BFF 只通过 payment 契约读取/关闭支付单并冻结渠道退款资金。
 */
@Component
public class PaymentOrderRemoteAdapter extends AbstractPaymentRemoteAdapter
    implements PaymentOrderQueryApi, PaymentOrderCommandApi {

    public PaymentOrderRemoteAdapter(PaymentServiceClient client) {
        super(client);
    }

    @Override
    public PaymentOrder findByPaymentNo(String paymentNo) {
        return unwrap(client.findByPaymentNo(paymentNo));
    }

    @Override
    public List<PaymentOrder> listByOrderNoAsc(String orderNo) {
        return unwrap(client.listByOrderNoAsc(orderNo));
    }

    @Override
    public List<PaymentOrder> listByChannelAndOrderNos(String channel, Collection<String> orderNos) {
        return unwrap(client.listByChannelAndOrderNos(orderNosRequest(channel, orderNos)));
    }

    @Override
    public List<PaymentOrder> listByChannelAndChannelOrderNos(String channel, Collection<String> channelOrderNos) {
        return unwrap(client.listByChannelAndChannelOrderNos(orderNosRequest(channel, channelOrderNos)));
    }

    @Override
    public List<PaymentOrder> listSuccessByChannelAndPaidTimeRange(String channel, Date startInclusive, Date endExclusive) {
        InternalPaidTimeRangeRequest request = new InternalPaidTimeRangeRequest();
        request.setChannel(channel);
        request.setStartInclusiveMillis(startInclusive.getTime());
        request.setEndExclusiveMillis(endExclusive.getTime());
        return unwrap(client.listSuccessByChannelAndPaidTimeRange(request));
    }

    @Override
    public List<PaymentOrder> listByPaymentNos(Collection<String> paymentNos) {
        return unwrap(client.listByPaymentNos(orderNosRequest(null, paymentNos)));
    }

    @Override
    public PaymentOrder findSuccessPaymentOrderForRefund(String orderNo) {
        return unwrap(client.findSuccessPaymentOrderForRefund(orderNo));
    }

    @Override
    public boolean hasSuccessfulPayment(String orderNo) {
        Boolean result = unwrap(client.hasSuccessfulPayment(orderNo));
        return Boolean.TRUE.equals(result);
    }

    @Override
    public int closeActiveByOrderNo(String orderNo) {
        Integer result = unwrap(client.closeActiveByOrderNo(orderNo));
        return result == null ? 0 : result;
    }

    @Override
    public int freezeChannelRefund(String paymentNo, Integer amount) {
        Integer result = unwrap(client.freezeChannelRefund(paymentNo, amount));
        return result == null ? 0 : result;
    }

    @Override
    public int settleChannelRefund(String paymentNo, Integer amount) {
        Integer result = unwrap(client.settleChannelRefund(paymentNo, amount));
        return result == null ? 0 : result;
    }

    private InternalOrderNosRequest orderNosRequest(String channel, Collection<String> orderNos) {
        InternalOrderNosRequest request = new InternalOrderNosRequest();
        request.setChannel(channel);
        request.setOrderNos(orderNos);
        return request;
    }
}
