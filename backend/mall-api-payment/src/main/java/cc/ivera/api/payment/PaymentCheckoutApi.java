package cc.ivera.api.payment;

import cc.ivera.payment.application.dto.WxPayNativeVO;

/**
 * 收银台支付发起契约：订单侧只负责校验可支付，渠道下单由支付服务完成。
 */
public interface PaymentCheckoutApi {

    String createAliPayForm(String orderNo);

    WxPayNativeVO createWxPayNative(String orderNo);
}
