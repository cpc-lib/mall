package cc.ivera.api.order;

import cc.ivera.order.domain.model.OrderInfo;

/**
 * 支付服务需要的订单契约：读取订单快照、按渠道建单/复用、保存支付二维码。
 */
public interface OrderPaymentApi {

    OrderInfo getOrderByOrderNo(String orderNo);

    OrderInfo createOrReuseOrder(Long productId, String paymentType, Long paymentAppId, String paymentChannelCode);

    void saveCodeUrl(String orderNo, String codeUrl);

    String getOrderStatus(String orderNo);
}
