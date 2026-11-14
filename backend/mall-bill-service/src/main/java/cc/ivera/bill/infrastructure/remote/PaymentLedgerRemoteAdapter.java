package cc.ivera.bill.infrastructure.remote;

import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.bill.infrastructure.remote.client.PaymentLedgerFeignClient;
import cc.ivera.payment.application.dto.InternalOrderNosRequest;
import cc.ivera.payment.application.dto.InternalPaidTimeRangeRequest;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 支付账远程适配器：账单上下文通过 payment-service 内部契约读取平台支付账，
 * 保留订单 1:N 支付尝试语义，禁止按 order_no 聚合。
 */
@Component
public class PaymentLedgerRemoteAdapter implements PaymentOrderQueryApi {

    private final PaymentLedgerFeignClient client;

    public PaymentLedgerRemoteAdapter(PaymentLedgerFeignClient client) {
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
        Boolean result = unwrap(client.hasSuccessfulPayment(orderNo));
        return Boolean.TRUE.equals(result);
    }

    private InternalOrderNosRequest orderNosRequest(String channel, Collection<String> orderNos) {
        InternalOrderNosRequest request = new InternalOrderNosRequest();
        request.setChannel(channel);
        request.setOrderNos(orderNos);
        return request;
    }

    private <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("支付服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "支付服务调用失败" : message);
    }
}
