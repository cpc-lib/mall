package cc.ivera.order.infrastructure.remote;

import cc.ivera.api.payment.PaymentOrderCommandApi;
import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.order.infrastructure.remote.client.PaymentServiceClient;
import cc.ivera.payment.application.dto.InternalOrderNosRequest;
import cc.ivera.payment.application.dto.InternalPaidTimeRangeRequest;
import cc.ivera.payment.domain.model.PaymentOrder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 支付单远程适配器：订单服务只通过支付契约读取/关闭支付尝试。
 */
@Component
public class PaymentOrderRemoteAdapter implements PaymentOrderQueryApi, PaymentOrderCommandApi {
    private static final String SERVICE = "支付服务";

    private final PaymentServiceClient client;

    public PaymentOrderRemoteAdapter(PaymentServiceClient client) {
        this.client = client;
    }

    @Override
    public PaymentOrder findByPaymentNo(String paymentNo) {
        return RemoteResponseUnwrapper.unwrap(client.findByPaymentNo(paymentNo), SERVICE);
    }

    @Override
    public List<PaymentOrder> listByOrderNoAsc(String orderNo) {
        return RemoteResponseUnwrapper.unwrap(client.listByOrderNoAsc(orderNo), SERVICE);
    }

    @Override
    public List<PaymentOrder> listByChannelAndOrderNos(String channel, Collection<String> orderNos) {
        return RemoteResponseUnwrapper.unwrap(
            client.listByChannelAndOrderNos(orderNosRequest(channel, orderNos)), SERVICE);
    }

    @Override
    public List<PaymentOrder> listByChannelAndChannelOrderNos(String channel, Collection<String> channelOrderNos) {
        return RemoteResponseUnwrapper.unwrap(
            client.listByChannelAndChannelOrderNos(orderNosRequest(channel, channelOrderNos)), SERVICE);
    }

    @Override
    public List<PaymentOrder> listSuccessByChannelAndPaidTimeRange(String channel, Date startInclusive, Date endExclusive) {
        InternalPaidTimeRangeRequest request = new InternalPaidTimeRangeRequest();
        request.setChannel(channel);
        request.setStartInclusiveMillis(startInclusive.getTime());
        request.setEndExclusiveMillis(endExclusive.getTime());
        return RemoteResponseUnwrapper.unwrap(client.listSuccessByChannelAndPaidTimeRange(request), SERVICE);
    }

    @Override
    public List<PaymentOrder> listByPaymentNos(Collection<String> paymentNos) {
        return RemoteResponseUnwrapper.unwrap(
            client.listByPaymentNos(orderNosRequest(null, paymentNos)), SERVICE);
    }

    @Override
    public PaymentOrder findSuccessPaymentOrderForRefund(String orderNo) {
        return RemoteResponseUnwrapper.unwrap(client.findSuccessPaymentOrderForRefund(orderNo), SERVICE);
    }

    @Override
    public boolean hasSuccessfulPayment(String orderNo) {
        Boolean result = RemoteResponseUnwrapper.unwrap(client.hasSuccessfulPayment(orderNo), SERVICE);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public int closeActiveByOrderNo(String orderNo) {
        Integer result = RemoteResponseUnwrapper.unwrap(client.closeActiveByOrderNo(orderNo), SERVICE);
        return result == null ? 0 : result;
    }

    @Override
    public int freezeChannelRefund(String paymentNo, Integer amount) {
        Integer result = RemoteResponseUnwrapper.unwrap(client.freezeChannelRefund(paymentNo, amount), SERVICE);
        return result == null ? 0 : result;
    }

    @Override
    public int settleChannelRefund(String paymentNo, Integer amount) {
        Integer result = RemoteResponseUnwrapper.unwrap(client.settleChannelRefund(paymentNo, amount), SERVICE);
        return result == null ? 0 : result;
    }

    private InternalOrderNosRequest orderNosRequest(String channel, Collection<String> orderNos) {
        InternalOrderNosRequest request = new InternalOrderNosRequest();
        request.setChannel(channel);
        request.setOrderNos(orderNos);
        return request;
    }
}
