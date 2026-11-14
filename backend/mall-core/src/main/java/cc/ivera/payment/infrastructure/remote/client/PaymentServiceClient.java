package cc.ivera.payment.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.payment.application.dto.*;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 支付服务内部契约客户端：路径与参数对齐 mall-payment-service 的 PaymentInternalController。
 */
@FeignClient(name = "mall-payment-service", url = "${payment.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface PaymentServiceClient {

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

    @PostMapping("/internal/payment/orders/{orderNo}/close-active")
    R<Integer> closeActiveByOrderNo(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/payment/orders/{paymentNo}/freeze-channel-refund")
    R<Integer> freezeChannelRefund(@PathVariable("paymentNo") String paymentNo,
                                   @RequestParam("amount") Integer amount);

    @PostMapping("/internal/payment/orders/{paymentNo}/settle-channel-refund")
    R<Integer> settleChannelRefund(@PathVariable("paymentNo") String paymentNo,
                                   @RequestParam("amount") Integer amount);

    @PostMapping("/internal/payment/channel-refunds/execute")
    R<ChannelRefundResult> executeRefund(@RequestBody ChannelRefundCommand command);

    @PostMapping("/internal/payment/channel-refunds/query")
    R<ChannelRefundStatus> queryRefund(@RequestBody ChannelRefundQuery query);

    @PostMapping("/internal/payment/channel-refunds/sync-query")
    R<ChannelRefundSyncResult> queryRefundStatusForSync(@RequestBody ChannelRefundQuery query);

    @GetMapping("/internal/payment/channel-refunds/order/{orderNo}")
    R<List<ChannelRefundSyncResult>> queryOrderRefundsForSync(@PathVariable("orderNo") String orderNo,
                                                              @RequestParam("channel") String channel);

    @PostMapping("/internal/payment/channel-refunds/notify")
    R<ChannelRefundSyncResult> processRefundNotify(@RequestBody Map<String, Object> bodyMap);

    @PostMapping("/internal/payment/config/reload")
    R<Object> reloadConfigs();

    @GetMapping("/internal/payment/config/apps/{appId}")
    R<PaymentAppConfig> getAppConfig(@PathVariable("appId") Long appId);

    @GetMapping("/internal/payment/config/apps/{appId}/required")
    R<PaymentAppConfig> getRequiredAppConfig(@PathVariable("appId") Long appId);

    @GetMapping("/internal/payment/config/apps/by-code/{appCode}")
    R<PaymentAppConfig> getAppConfigByCode(@PathVariable("appCode") String appCode);

    @GetMapping("/internal/payment/config/apps/default/{channelCode}")
    R<PaymentAppConfig> getDefaultAppConfigByChannelCode(@PathVariable("channelCode") String channelCode);

    @GetMapping("/internal/payment/config/apps/default/{channelCode}/required")
    R<PaymentAppConfig> getRequiredDefaultAppConfigByChannelCode(@PathVariable("channelCode") String channelCode);

    @GetMapping("/internal/payment/config/apps/by-channel/{channelCode}")
    R<List<PaymentAppConfig>> listAppConfigsByChannelCode(@PathVariable("channelCode") String channelCode);

    @GetMapping("/internal/payment/config/apps")
    R<Map<Long, PaymentAppConfig>> getAllAppConfigs();

    @PostMapping("/internal/payment/success/handle")
    R<Boolean> handlePaymentSuccess(@RequestBody InternalPaymentSuccessRequest request);

    @PostMapping("/internal/payment/success/offline-paid/{orderNo}")
    R<Boolean> markOfflinePaid(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/payment/channel-query")
    R<ChannelOrderQueryVO> queryChannelOrder(@RequestBody InternalChannelRequest request);

    @PostMapping("/internal/payment/channel-status")
    R<Object> checkChannelOrderStatus(@RequestBody InternalChannelRequest request);

    @PostMapping("/internal/payment/checkout/alipay/{orderNo}")
    R<String> createAliPayForm(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/payment/checkout/wxpay/{orderNo}")
    R<WxPayNativeVO> createWxPayNative(@PathVariable("orderNo") String orderNo);
}
