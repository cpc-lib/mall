package cc.ivera.payment.infrastructure.gateway;

import cc.ivera.api.payment.PaymentOrderCommandApi;
import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentDealRepository;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 支付上下文对外契约的进程内实现：只委托支付单仓储，不附加业务规则。
 * 支付服务拆分后，本类由远程客户端替换，契约不变。
 */
@Component
@RequiredArgsConstructor
public class PaymentInternalApiAdapter implements PaymentOrderQueryApi, PaymentOrderCommandApi {

    private final PaymentOrderRepository paymentOrderRepository;

    private final PaymentDealRepository paymentDealRepository;

    @Override
    public PaymentOrder findByPaymentNo(String paymentNo) {
        return paymentOrderRepository.findByPaymentNo(paymentNo);
    }

    @Override
    public List<PaymentOrder> listByOrderNoAsc(String orderNo) {
        return paymentOrderRepository.listByOrderNoAsc(orderNo);
    }

    @Override
    public List<PaymentOrder> listByChannelAndOrderNos(String channel, Collection<String> orderNos) {
        return paymentOrderRepository.listByChannelAndOrderNos(channel, orderNos);
    }

    @Override
    public List<PaymentOrder> listByChannelAndChannelOrderNos(String channel, Collection<String> channelOrderNos) {
        return paymentOrderRepository.listByChannelAndChannelOrderNos(channel, channelOrderNos);
    }

    @Override
    public List<PaymentOrder> listSuccessByChannelAndPaidTimeRange(String channel, Date startInclusive, Date endExclusive) {
        return paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(channel, startInclusive, endExclusive);
    }

    @Override
    public List<PaymentOrder> listByPaymentNos(Collection<String> paymentNos) {
        return paymentOrderRepository.listByPaymentNos(paymentNos);
    }

    @Override
    public PaymentOrder findSuccessPaymentOrderForRefund(String orderNo) {
        return paymentOrderRepository.findSuccessPaymentOrderForRefund(orderNo);
    }

    @Override
    public boolean hasSuccessfulPayment(String orderNo) {
        return paymentDealRepository.existsByOrderNo(orderNo);
    }

    @Override
    public int closeActiveByOrderNo(String orderNo) {
        return paymentOrderRepository.closeActiveByOrderNo(orderNo);
    }

    @Override
    public int freezeChannelRefund(String paymentNo, Integer amount) {
        return paymentOrderRepository.freezeChannelRefund(paymentNo, amount);
    }

    @Override
    public int settleChannelRefund(String paymentNo, Integer amount) {
        return paymentOrderRepository.settleChannelRefund(paymentNo, amount);
    }
}
