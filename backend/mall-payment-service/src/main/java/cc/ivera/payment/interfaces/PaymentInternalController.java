package cc.ivera.payment.interfaces;

import cc.ivera.api.payment.PaymentChannelRefundApi;
import cc.ivera.api.payment.PaymentCheckoutApi;
import cc.ivera.api.payment.PaymentOrderCommandApi;
import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.payment.application.ChannelOrderStatusApi;
import cc.ivera.payment.application.ChannelPaymentQueryApi;
import cc.ivera.payment.application.PaymentSuccessService;
import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;
import cc.ivera.payment.application.dto.InternalChannelRequest;
import cc.ivera.payment.application.dto.InternalOrderNosRequest;
import cc.ivera.payment.application.dto.InternalPaidTimeRangeRequest;
import cc.ivera.payment.application.dto.InternalPaymentSuccessRequest;
import cc.ivera.payment.application.dto.WxPayNativeVO;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 支付服务内部契约：由 BFF 通过 /internal/payment/** 调用，不经过网关。
 */
@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    private final PaymentOrderQueryApi paymentOrderQueryApi;

    private final PaymentOrderCommandApi paymentOrderCommandApi;

    private final PaymentChannelRefundApi paymentChannelRefundApi;

    private final PaymentConfigGateway paymentConfigGateway;

    private final PaymentSuccessService paymentSuccessService;

    private final ChannelPaymentQueryApi channelPaymentQueryApi;

    private final ChannelOrderStatusApi channelOrderStatusApi;

    private final PaymentCheckoutApi paymentCheckoutApi;

    public PaymentInternalController(PaymentOrderQueryApi paymentOrderQueryApi,
                                     PaymentOrderCommandApi paymentOrderCommandApi,
                                     PaymentChannelRefundApi paymentChannelRefundApi,
                                     PaymentConfigGateway paymentConfigGateway,
                                     PaymentSuccessService paymentSuccessService,
                                     ChannelPaymentQueryApi channelPaymentQueryApi,
                                     ChannelOrderStatusApi channelOrderStatusApi,
                                     PaymentCheckoutApi paymentCheckoutApi) {
        this.paymentOrderQueryApi = paymentOrderQueryApi;
        this.paymentOrderCommandApi = paymentOrderCommandApi;
        this.paymentChannelRefundApi = paymentChannelRefundApi;
        this.paymentConfigGateway = paymentConfigGateway;
        this.paymentSuccessService = paymentSuccessService;
        this.channelPaymentQueryApi = channelPaymentQueryApi;
        this.channelOrderStatusApi = channelOrderStatusApi;
        this.paymentCheckoutApi = paymentCheckoutApi;
    }

    @GetMapping("/orders/{paymentNo}")
    public R<PaymentOrder> findByPaymentNo(@PathVariable String paymentNo) {
        return R.ok(paymentOrderQueryApi.findByPaymentNo(paymentNo));
    }

    @GetMapping("/orders/by-order/{orderNo}")
    public R<List<PaymentOrder>> listByOrderNoAsc(@PathVariable String orderNo) {
        return R.ok(paymentOrderQueryApi.listByOrderNoAsc(orderNo));
    }

    @PostMapping("/orders/by-order-nos")
    public R<List<PaymentOrder>> listByChannelAndOrderNos(@RequestBody InternalOrderNosRequest request) {
        return R.ok(paymentOrderQueryApi.listByChannelAndOrderNos(request.getChannel(), request.getOrderNos()));
    }

    @PostMapping("/orders/by-channel-order-nos")
    public R<List<PaymentOrder>> listByChannelAndChannelOrderNos(@RequestBody InternalOrderNosRequest request) {
        return R.ok(paymentOrderQueryApi.listByChannelAndChannelOrderNos(request.getChannel(), request.getOrderNos()));
    }

    @PostMapping("/orders/success-by-paid-time")
    public R<List<PaymentOrder>> listSuccessByChannelAndPaidTimeRange(@RequestBody InternalPaidTimeRangeRequest request) {
        return R.ok(paymentOrderQueryApi.listSuccessByChannelAndPaidTimeRange(request.getChannel(),
            new Date(request.getStartInclusiveMillis()), new Date(request.getEndExclusiveMillis())));
    }

    @PostMapping("/orders/by-payment-nos")
    public R<List<PaymentOrder>> listByPaymentNos(@RequestBody InternalOrderNosRequest request) {
        return R.ok(paymentOrderQueryApi.listByPaymentNos(request.getOrderNos()));
    }

    @GetMapping("/orders/success-for-refund/{orderNo}")
    public R<PaymentOrder> findSuccessPaymentOrderForRefund(@PathVariable String orderNo) {
        return R.ok(paymentOrderQueryApi.findSuccessPaymentOrderForRefund(orderNo));
    }

    @GetMapping("/orders/{orderNo}/has-successful-payment")
    public R<Boolean> hasSuccessfulPayment(@PathVariable String orderNo) {
        return R.ok(paymentOrderQueryApi.hasSuccessfulPayment(orderNo));
    }

    @PostMapping("/orders/{orderNo}/close-active")
    public R<Integer> closeActiveByOrderNo(@PathVariable String orderNo) {
        return R.ok(paymentOrderCommandApi.closeActiveByOrderNo(orderNo));
    }

    @PostMapping("/orders/{paymentNo}/freeze-channel-refund")
    public R<Integer> freezeChannelRefund(@PathVariable String paymentNo, @RequestParam Integer amount) {
        return R.ok(paymentOrderCommandApi.freezeChannelRefund(paymentNo, amount));
    }

    @PostMapping("/orders/{paymentNo}/settle-channel-refund")
    public R<Integer> settleChannelRefund(@PathVariable String paymentNo, @RequestParam Integer amount) {
        return R.ok(paymentOrderCommandApi.settleChannelRefund(paymentNo, amount));
    }

    @PostMapping("/channel-refunds/execute")
    public R<ChannelRefundResult> executeRefund(@RequestBody ChannelRefundCommand command) {
        return R.ok(paymentChannelRefundApi.executeRefund(command));
    }

    @PostMapping("/channel-refunds/query")
    public R<ChannelRefundStatus> queryRefund(@RequestBody ChannelRefundQuery query) {
        return R.ok(paymentChannelRefundApi.queryRefund(query));
    }

    @PostMapping("/channel-refunds/sync-query")
    public R<ChannelRefundSyncResult> queryRefundStatusForSync(@RequestBody ChannelRefundQuery query) {
        return R.ok(paymentChannelRefundApi.queryRefundStatusForSync(query));
    }

    @GetMapping("/channel-refunds/order/{orderNo}")
    public R<List<ChannelRefundSyncResult>> queryOrderRefundsForSync(@PathVariable String orderNo,
                                                                     @RequestParam String channel) {
        return R.ok(paymentChannelRefundApi.queryOrderRefundsForSync(orderNo, channel));
    }

    @PostMapping("/channel-refunds/notify")
    public R<ChannelRefundSyncResult> processRefundNotify(@RequestBody Map<String, Object> bodyMap) {
        return R.ok(paymentChannelRefundApi.processRefundNotify(bodyMap));
    }

    @PostMapping("/config/reload")
    public R<?> reloadConfigs() {
        paymentConfigGateway.reloadConfigs();
        return R.ok();
    }

    @GetMapping("/config/apps/{appId}")
    public R<PaymentAppConfig> getAppConfig(@PathVariable Long appId) {
        return R.ok(paymentConfigGateway.getAppConfig(appId));
    }

    @GetMapping("/config/apps/{appId}/required")
    public R<PaymentAppConfig> getRequiredAppConfig(@PathVariable Long appId) {
        return R.ok(paymentConfigGateway.getRequiredAppConfig(appId));
    }

    @GetMapping("/config/apps/by-code/{appCode}")
    public R<PaymentAppConfig> getAppConfigByCode(@PathVariable String appCode) {
        return R.ok(paymentConfigGateway.getAppConfigByCode(appCode));
    }

    @GetMapping("/config/apps/default/{channelCode}")
    public R<PaymentAppConfig> getDefaultAppConfigByChannelCode(@PathVariable String channelCode) {
        return R.ok(paymentConfigGateway.getDefaultAppConfigByChannelCode(channelCode));
    }

    @GetMapping("/config/apps/default/{channelCode}/required")
    public R<PaymentAppConfig> getRequiredDefaultAppConfigByChannelCode(@PathVariable String channelCode) {
        return R.ok(paymentConfigGateway.getRequiredDefaultAppConfigByChannelCode(channelCode));
    }

    @GetMapping("/config/apps/by-channel/{channelCode}")
    public R<List<PaymentAppConfig>> listAppConfigsByChannelCode(@PathVariable String channelCode) {
        return R.ok(paymentConfigGateway.listAppConfigsByChannelCode(channelCode));
    }

    @GetMapping("/config/apps")
    public R<Map<Long, PaymentAppConfig>> getAllAppConfigs() {
        return R.ok(paymentConfigGateway.getAllAppConfigs());
    }

    @PostMapping("/success/handle")
    public R<Boolean> handlePaymentSuccess(@RequestBody InternalPaymentSuccessRequest request) {
        return R.ok(paymentSuccessService.handlePaymentSuccess(request.getOrderNo(), request.getChannel(),
            request.getChannelOrderNo(), request.getPaidAmount()));
    }

    @PostMapping("/success/offline-paid/{orderNo}")
    public R<Boolean> markOfflinePaid(@PathVariable String orderNo) {
        return R.ok(paymentSuccessService.markOfflinePaid(orderNo));
    }

    @PostMapping("/channel-query")
    public R<ChannelOrderQueryVO> queryChannelOrder(@RequestBody InternalChannelRequest request) {
        return R.ok(channelPaymentQueryApi.query(request.getPayType(), request.getOrderNo()));
    }

    @PostMapping("/channel-status")
    public R<?> checkChannelOrderStatus(@RequestBody InternalChannelRequest request) {
        channelOrderStatusApi.checkOrderStatus(request.getPayType(), request.getOrderNo());
        return R.ok();
    }

    @PostMapping("/checkout/alipay/{orderNo}")
    public R<String> createAliPayForm(@PathVariable String orderNo) {
        return R.ok(paymentCheckoutApi.createAliPayForm(orderNo));
    }

    @PostMapping("/checkout/wxpay/{orderNo}")
    public R<WxPayNativeVO> createWxPayNative(@PathVariable String orderNo) {
        return R.ok(paymentCheckoutApi.createWxPayNative(orderNo));
    }
}
