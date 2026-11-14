package cc.ivera.api.payment;

import cc.ivera.payment.domain.model.PaymentOrder;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 支付单只读契约：保留订单 1:N 支付尝试语义，禁止按订单号折叠后返回给对账方。
 */
public interface PaymentOrderQueryApi {

    PaymentOrder findByPaymentNo(String paymentNo);

    List<PaymentOrder> listByOrderNoAsc(String orderNo);

    List<PaymentOrder> listByChannelAndOrderNos(String channel, Collection<String> orderNos);

    List<PaymentOrder> listByChannelAndChannelOrderNos(String channel, Collection<String> channelOrderNos);

    List<PaymentOrder> listSuccessByChannelAndPaidTimeRange(String channel, Date startInclusive, Date endExclusive);

    List<PaymentOrder> listByPaymentNos(Collection<String> paymentNos);

    PaymentOrder findSuccessPaymentOrderForRefund(String orderNo);

    /**
     * 该业务订单是否存在有效支付成交事实（t_payment_deal）。
     */
    boolean hasSuccessfulPayment(String orderNo);
}
