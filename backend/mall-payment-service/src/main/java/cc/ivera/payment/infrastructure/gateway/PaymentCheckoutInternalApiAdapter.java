package cc.ivera.payment.infrastructure.gateway;

import cc.ivera.api.payment.PaymentCheckoutApi;
import cc.ivera.payment.application.AliPayService;
import cc.ivera.payment.application.dto.WxPayNativeVO;
import cc.ivera.payment.application.wxpay.WxPayOrderFacade;
import org.springframework.stereotype.Component;

/**
 * 收银台支付发起本地实现：暴露给 BFF 的远程适配器调用。
 */
@Component
public class PaymentCheckoutInternalApiAdapter implements PaymentCheckoutApi {

    private final AliPayService aliPayService;

    private final WxPayOrderFacade wxPayOrderFacade;

    public PaymentCheckoutInternalApiAdapter(AliPayService aliPayService, WxPayOrderFacade wxPayOrderFacade) {
        this.aliPayService = aliPayService;
        this.wxPayOrderFacade = wxPayOrderFacade;
    }

    @Override
    public String createAliPayForm(String orderNo) {
        return aliPayService.tradeCreateByOrderNo(orderNo);
    }

    @Override
    public WxPayNativeVO createWxPayNative(String orderNo) {
        return wxPayOrderFacade.nativePayByOrderNo(orderNo);
    }
}
