package cc.ivera.order.application;

/**
 * 延迟关单消息发件箱应用服务。
 */
public interface OrderCloseMessageService {

    void sendCloseOrderMessage(String orderNo, String paymentType);
}
