package cc.ivera.controller;

import cc.ivera.dto.checkout.CheckoutRequest;
import cc.ivera.entity.OrderInfo;
import cc.ivera.enums.OrderStatus;
import cc.ivera.exception.BizException;
import cc.ivera.security.AuthContext;
import cc.ivera.service.AliPayService;
import cc.ivera.service.CheckoutService;
import cc.ivera.service.wxpay.WxPayOrderFacade;
import cc.ivera.vo.AlipayVO;
import cc.ivera.vo.OrderDetailVO;
import cc.ivera.vo.R;
import cc.ivera.vo.WxPayNativeVO;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController @RequestMapping("/api/checkout") @CrossOrigin
public class CheckoutController {
    private final CheckoutService checkoutService; private final AliPayService aliPayService; private final WxPayOrderFacade wxPayOrderFacade;
    public CheckoutController(CheckoutService checkoutService,AliPayService aliPayService,WxPayOrderFacade wxPayOrderFacade){this.checkoutService=checkoutService;this.aliPayService=aliPayService;this.wxPayOrderFacade=wxPayOrderFacade;}
    @PostMapping("/orders") public R<OrderDetailVO> create(@Valid @RequestBody CheckoutRequest req){return R.ok(checkoutService.createOrder(AuthContext.userId(),req));}
    @GetMapping("/orders") public R<List<OrderDetailVO>> list(){return R.ok(checkoutService.listMyOrders(AuthContext.userId()));}
    @GetMapping("/orders/{orderNo}") public R<OrderDetailVO> get(@PathVariable String orderNo){return R.ok(checkoutService.getOrder(AuthContext.userId(),orderNo));}
    @PostMapping("/orders/{orderNo}/alipay") public R<AlipayVO> alipay(@PathVariable String orderNo){OrderDetailVO d=checkoutService.getOrder(AuthContext.userId(),orderNo);ensurePayable(d.getOrder());AlipayVO vo=new AlipayVO();vo.setHtml(aliPayService.tradeCreateByOrderNo(orderNo));vo.setOrderNo(orderNo);return R.ok(vo);}
    @PostMapping("/orders/{orderNo}/wxpay") public R<WxPayNativeVO> wxpay(@PathVariable String orderNo){OrderDetailVO d=checkoutService.getOrder(AuthContext.userId(),orderNo);ensurePayable(d.getOrder());return R.ok(wxPayOrderFacade.nativePayByOrderNo(orderNo));}
    private void ensurePayable(OrderInfo order){if(!OrderStatus.NOTPAY.getType().equals(order.getLegacyStatus()))throw new BizException("订单当前状态不可支付："+order.getLegacyStatus());}
}
