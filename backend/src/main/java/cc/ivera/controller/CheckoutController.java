package cc.ivera.controller;

import cc.ivera.dto.checkout.CheckoutRequest;
import cc.ivera.entity.OrderInfo;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayStatus;
import cc.ivera.enums.PayType;
import cc.ivera.exception.BizException;
import cc.ivera.security.AuthContext;
import cc.ivera.service.AliPayService;
import cc.ivera.service.CheckoutService;
import cc.ivera.service.wxpay.WxPayOrderFacade;
import cc.ivera.vo.AlipayVO;
import cc.ivera.vo.ChannelOrderQueryVO;
import cc.ivera.vo.OrderDetailVO;
import cc.ivera.vo.PayQueryVO;
import cc.ivera.vo.R;
import cc.ivera.vo.WxPayNativeVO;
import cc.ivera.vo.WxPayStatusVO;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController @RequestMapping("/api/checkout") @CrossOrigin
public class CheckoutController {
    private final CheckoutService checkoutService; private final AliPayService aliPayService; private final WxPayOrderFacade wxPayOrderFacade;
    public CheckoutController(CheckoutService checkoutService,AliPayService aliPayService,WxPayOrderFacade wxPayOrderFacade){this.checkoutService=checkoutService;this.aliPayService=aliPayService;this.wxPayOrderFacade=wxPayOrderFacade;}
    @PostMapping("/orders") public R<OrderDetailVO> create(@Valid @RequestBody CheckoutRequest req){return R.ok(checkoutService.createOrder(AuthContext.userId(),req));}
    @GetMapping("/orders") public R<List<OrderDetailVO>> list(){return R.ok(checkoutService.listMyOrders(AuthContext.userId()));}
    @GetMapping("/orders/{orderNo}") public R<OrderDetailVO> get(@PathVariable String orderNo){return R.ok(checkoutService.getOrder(AuthContext.userId(),orderNo));}
    @PostMapping("/orders/{orderNo}/alipay") public R<AlipayVO> alipay(@PathVariable String orderNo){OrderDetailVO d=checkoutService.getOrder(AuthContext.userId(),orderNo);ensurePayable(d.getOrder());AlipayVO vo=new AlipayVO();vo.setHtml(aliPayService.tradeCreateByOrderNo(orderNo));vo.setOrderNo(orderNo);return R.ok(vo);}
    @PostMapping("/orders/{orderNo}/wxpay") public R<WxPayNativeVO> wxpay(@PathVariable String orderNo){OrderDetailVO d=checkoutService.getOrder(AuthContext.userId(),orderNo);ensurePayable(d.getOrder());return R.ok(wxPayOrderFacade.nativePayByOrderNo(orderNo));}

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
        if (PayStatus.PAID.getType().equals(order.getPayStatus())) {
            vo.setPayStatus(order.getPayStatus());
            vo.setOrderStatus(order.getLegacyStatus());
            vo.setChannelCode(order.getPaymentType());
            vo.setChannelTradeState("SUCCESS");
            vo.setChannelTradeStateDesc("支付成功");
            vo.setSynced(false);
            return R.ok(vo);
        }

        String before = order.getLegacyStatus();
        String channelTradeState;
        String channelTradeStateDesc;
        if (PayType.WXPAY.getType().equals(order.getPaymentType())) {
            WxPayStatusVO wx = wxPayOrderFacade.queryPaymentStatus(orderNo);
            channelTradeState = wx.getTradeState();
            channelTradeStateDesc = wx.getTradeStateDesc();
            vo.setSynced(!Objects.equals(wx.getLocalStatusBefore(), wx.getLocalStatus()));
        } else if (PayType.ALIPAY.getType().equals(order.getPaymentType())) {
            ChannelOrderQueryVO ali = aliPayService.queryAndSyncStatus(orderNo);
            channelTradeState = ali.getChannelTradeState();
            channelTradeStateDesc = ali.getChannelTradeStateDesc();
            vo.setSynced(Boolean.TRUE.equals(ali.getSynced()));
        } else {
            throw new BizException("该订单无支付渠道，无法查询支付结果");
        }

        OrderInfo after = checkoutService.getOrder(AuthContext.userId(), orderNo).getOrder();
        vo.setPayStatus(after.getPayStatus());
        vo.setOrderStatus(after.getLegacyStatus());
        vo.setChannelCode(order.getPaymentType());
        vo.setChannelTradeState(channelTradeState);
        vo.setChannelTradeStateDesc(channelTradeStateDesc);
        if (Boolean.TRUE.equals(vo.getSynced()) || !before.equals(after.getLegacyStatus())) {
            vo.setSynced(true);
        }
        return R.ok(vo);
    }

    private void ensurePayable(OrderInfo order){if(!OrderStatus.NOTPAY.getType().equals(order.getLegacyStatus()))throw new BizException("订单当前状态不可支付："+order.getLegacyStatus());}
}
