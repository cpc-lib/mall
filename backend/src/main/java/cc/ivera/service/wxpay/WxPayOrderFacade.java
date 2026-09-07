package cc.ivera.service.wxpay;

import cc.ivera.entity.OrderInfo;
import cc.ivera.vo.WxPayJsapiVO;
import cc.ivera.vo.WxPayNativeVO;
import cc.ivera.vo.WxPayStatusVO;

import java.util.Map;

public interface WxPayOrderFacade {

    WxPayNativeVO nativePay(Long productId);

    WxPayNativeVO nativePay(Long productId, Long paymentAppId);

    WxPayNativeVO nativePayByOrderNo(String orderNo);

    void processOrder(Map<String, Object> bodyMap);

    void cancelOrder(String orderNo);

    String queryOrder(String orderNo);

    WxPayStatusVO queryPaymentStatus(String orderNo);

    void checkOrderStatus(String orderNo);

    WxPayNativeVO nativePayV2(Long productId, String remoteAddr);

    WxPayNativeVO nativePayV2(Long productId, String remoteAddr, Long paymentAppId);

    WxPayJsapiVO jsapiPay(OrderInfo orderInfo, String openid);
}
