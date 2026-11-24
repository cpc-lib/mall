package cc.ivera.order.interfaces;


import cc.ivera.api.payment.PaymentCheckoutApi;
import cc.ivera.order.application.CheckoutService;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.application.dto.CheckoutRequest;
import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.payment.application.ChannelPaymentQueryApi;
import cc.ivera.payment.application.dto.AlipayVO;
import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.payment.application.dto.PayQueryVO;
import cc.ivera.payment.application.dto.WxPayNativeVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/checkout")
@CrossOrigin
public class CheckoutController {
    private final CheckoutService checkoutService;
    private final PaymentCheckoutApi paymentCheckoutApi;
    private final ChannelPaymentQueryApi channelPaymentQueryDispatcher;

    public CheckoutController(CheckoutService checkoutService,
                              PaymentCheckoutApi paymentCheckoutApi,
                              ChannelPaymentQueryApi channelPaymentQueryDispatcher) {
        this.checkoutService = checkoutService;
        this.paymentCheckoutApi = paymentCheckoutApi;
        this.channelPaymentQueryDispatcher = channelPaymentQueryDispatcher;
    }

    /**
     * 下单：必须携带 Idempotency-Key，重复提交返回首次结果而不是重复建单。
     */
    @PostMapping("/orders")
    public R<OrderDetailVO> create(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @Valid @RequestBody CheckoutRequest req) {
        return R.ok(checkoutService.createOrder(AuthContext.userId(), idempotencyKey, req));
    }

    @GetMapping("/orders")
    public R<PageVO<OrderDetailVO>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(checkoutService.listMyOrders(AuthContext.userId(), page, size));
    }

    @GetMapping("/orders/{orderNo}")
    public R<OrderDetailVO> get(@PathVariable String orderNo) {
        return R.ok(checkoutService.getOrder(AuthContext.userId(), orderNo));
    }

    @PostMapping("/orders/{orderNo}/alipay")
    public R<AlipayVO> alipay(@PathVariable String orderNo) {
        OrderDetailVO d = checkoutService.getOrder(AuthContext.userId(), orderNo);
        ensurePayable(d.getOrder());
        AlipayVO vo = new AlipayVO();
        vo.setHtml(paymentCheckoutApi.createAliPayForm(orderNo));
        vo.setOrderNo(orderNo);
        return R.ok(vo);
    }

    @PostMapping("/orders/{orderNo}/wxpay")
    public R<WxPayNativeVO> wxpay(@PathVariable String orderNo) {
        OrderDetailVO d = checkoutService.getOrder(AuthContext.userId(), orderNo);
        ensurePayable(d.getOrder());
        return R.ok(paymentCheckoutApi.createWxPayNative(orderNo));
    }

    /**
     * 用户端主动查单：向渠道查询支付状态，渠道确认已支付则同步推进本地订单（幂等），
     * 返回同步后的本地支付状态。归属校验与既有订单详情接口同路径。
     */
    @GetMapping("/orders/{orderNo}/pay-query")
    public R<PayQueryVO> payQuery(@PathVariable String orderNo) {
        OrderDetailVO detail = checkoutService.getOrder(AuthContext.userId(), orderNo);
        OrderInfo order = detail.getOrder();
        PayQueryVO vo = new PayQueryVO();
        vo.setOrderNo(orderNo);
        // 已支付短路：本地已是 PAID 直接返回，不再调渠道。
        if (cc.ivera.shared.domain.enums.PayStatus.PAID.getType().equals(order.getPayStatus())) {
            vo.setPayStatus(order.getPayStatus());
            vo.setOrderStatus(order.getLegacyStatus());
            vo.setChannelCode(order.getPaymentType());
            vo.setChannelTradeState("SUCCESS");
            vo.setChannelTradeStateDesc("支付成功");
            vo.setSynced(false);
            return R.ok(vo);
        }

        String before = order.getLegacyStatus();
        ChannelOrderQueryVO channel = channelPaymentQueryDispatcher.query(order.getPaymentType(), orderNo);

        OrderInfo after = checkoutService.getOrder(AuthContext.userId(), orderNo).getOrder();
        vo.setPayStatus(after.getPayStatus());
        vo.setOrderStatus(after.getLegacyStatus());
        vo.setChannelCode(order.getPaymentType());
        vo.setChannelTradeState(channel.getChannelTradeState());
        vo.setChannelTradeStateDesc(channel.getChannelTradeStateDesc());
        if (Boolean.TRUE.equals(channel.getSynced()) || !before.equals(after.getLegacyStatus())) {
            vo.setSynced(true);
        }
        return R.ok(vo);
    }

    private void ensurePayable(OrderInfo order) {
        if (!order.isPayable())
            throw new BizException("订单当前状态不可支付：" + order.getLegacyStatus());
    }
}
