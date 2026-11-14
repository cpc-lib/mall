package cc.ivera.bill.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.payment.application.dto.InternalOrderNosRequest;
import cc.ivera.payment.application.dto.InternalPaidTimeRangeRequest;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 支付服务内部契约客户端：路径与 {@code PaymentInternalController} 保持一一对应。
 */
@FeignClient(name = "mall-payment-service", url = "${payment.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface PaymentLedgerFeignClient {

    @GetMapping("/internal/payment/orders/{paymentNo}")
    R<PaymentOrder> findByPaymentNo(@PathVariable("paymentNo") String paymentNo);

    @GetMapping("/internal/payment/orders/by-order/{orderNo}")
    R<List<PaymentOrder>> listByOrderNoAsc(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/payment/orders/by-order-nos")
    R<List<PaymentOrder>> listByChannelAndOrderNos(@RequestBody InternalOrderNosRequest request);

    @PostMapping("/internal/payment/orders/by-channel-order-nos")
    R<List<PaymentOrder>> listByChannelAndChannelOrderNos(@RequestBody InternalOrderNosRequest request);

    @PostMapping("/internal/payment/orders/success-by-paid-time")
    R<List<PaymentOrder>> listSuccessByChannelAndPaidTimeRange(@RequestBody InternalPaidTimeRangeRequest request);

    @PostMapping("/internal/payment/orders/by-payment-nos")
    R<List<PaymentOrder>> listByPaymentNos(@RequestBody InternalOrderNosRequest request);

    @GetMapping("/internal/payment/orders/success-for-refund/{orderNo}")
    R<PaymentOrder> findSuccessPaymentOrderForRefund(@PathVariable("orderNo") String orderNo);

    @GetMapping("/internal/payment/orders/{orderNo}/has-successful-payment")
    R<Boolean> hasSuccessfulPayment(@PathVariable("orderNo") String orderNo);
}
