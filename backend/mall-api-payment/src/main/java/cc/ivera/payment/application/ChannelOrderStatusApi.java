package cc.ivera.payment.application;

/**
 * 渠道关单核对契约：按支付方式查询渠道订单状态并决定是否可关闭。
 */
public interface ChannelOrderStatusApi {

    void checkOrderStatus(String payType, String orderNo);
}
