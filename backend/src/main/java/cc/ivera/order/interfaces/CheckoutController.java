package cc.ivera.order.interfaces;


import cc.ivera.order.application.CheckoutService;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.interfaces.dto.CheckoutRequest;
import cc.ivera.order.interfaces.vo.OrderDetailVO;
import cc.ivera.payment.application.AliPayService;
import cc.ivera.payment.application.ChannelPaymentQueryDispatcher;
import cc.ivera.payment.application.wxpay.WxPayOrderFacade;
import cc.ivera.payment.interfaces.vo.AlipayVO;
import cc.ivera.payment.interfaces.vo.ChannelOrderQueryVO;
import cc.ivera.payment.interfaces.vo.PayQueryVO;
import cc.ivera.payment.interfaces.vo.WxPayNativeVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/checkout")
@CrossOrigin
public class CheckoutController {
    private final CheckoutService checkoutService;
    private final AliPayService aliPayService;
    private final WxPayOrderFacade wxPayOrderFacade;
    private final ChannelPaymentQueryDispatcher channelPaymentQueryDispatcher;

    public CheckoutController(CheckoutService checkoutService,
                              AliPayService aliPayService,
                              WxPayOrderFacade wxPayOrderFacade,
                              ChannelPaymentQueryDispatcher channelPaymentQueryDispatcher) {
        this.checkoutService = checkoutService;
        this.aliPayService = aliPayService;
        this.wxPayOrderFacade = wxPayOrderFacade;
        this.channelPaymentQueryDispatcher = channelPaymentQueryDispatcher;
    }

    @PostMapping("/orders")
    public R<OrderDetailVO> create(@Valid @RequestBody CheckoutRequest req) {
        return R.ok(checkoutService.createOrder(AuthContext.userId(), req));
    }

    @GetMapping("/orders")
    public R<List<OrderDetailVO>> list() {
        return R.ok(checkoutService.listMyOrders(AuthContext.userId()));
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
        vo.setHtml(aliPayService.tradeCreateByOrderNo(orderNo));
        vo.setOrderNo(orderNo);
        return R.ok(vo);
    }

    @PostMapping("/orders/{orderNo}/wxpay")
    public R<WxPayNativeVO> wxpay(@PathVariable String orderNo) {
        OrderDetailVO d = checkoutService.getOrder(AuthContext.userId(), orderNo);
        ensurePayable(d.getOrder());
        return R.ok(wxPayOrderFacade.nativePayByOrderNo(orderNo));
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
        if (cc.ivera.payment.domain.enums.PayStatus.PAID.getType().equals(order.getPayStatus())) {
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
