package cc.ivera.service;

public interface OrderCloseMessageService {

    void sendCloseOrderMessage(String orderNo, String paymentType);
}
