package cc.ivera.refund.infrastructure.remote;

import cc.ivera.api.payment.PaymentOrderCommandApi;
import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.payment.application.dto.InternalOrderNosRequest;
import cc.ivera.payment.application.dto.InternalPaidTimeRangeRequest;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.refund.infrastructure.remote.client.PaymentServiceFeignClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 支付单远程适配器：退款上下文通过 payment 契约读取支付单并冻结/结转渠道退款资金。
 */
@Component
public class PaymentOrderRemoteAdapter extends AbstractRefundRemoteAdapter
    implements PaymentOrderQueryApi, PaymentOrderCommandApi {

    private final PaymentServiceFeignClient client;

    public PaymentOrderRemoteAdapter(PaymentServiceFeignClient client) {
        this.client = client;
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
        return Boolean.TRUE.equals(unwrap(client.hasSuccessfulPayment(orderNo)));
    }

    @Override
    public int closeActiveByOrderNo(String orderNo) {
        return orZero(unwrap(client.closeActiveByOrderNo(orderNo)));
    }

    @Override
    public int freezeChannelRefund(String paymentNo, Integer amount) {
        return orZero(unwrap(client.freezeChannelRefund(paymentNo, amount)));
    }

    @Override
    public int settleChannelRefund(String paymentNo, Integer amount) {
        return orZero(unwrap(client.settleChannelRefund(paymentNo, amount)));
    }

    private InternalOrderNosRequest orderNosRequest(String channel, Collection<String> orderNos) {
        InternalOrderNosRequest request = new InternalOrderNosRequest();
        request.setChannel(channel);
        request.setOrderNos(orderNos);
        return request;
    }

    private static int orZero(Integer result) {
        return result == null ? 0 : result;
    }
}
