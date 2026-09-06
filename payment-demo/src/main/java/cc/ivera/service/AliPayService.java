package cc.ivera.service;

import cc.ivera.entity.RefundInfo;
import cc.ivera.service.refund.RefundStatusSyncResult;

import java.util.Map;

public interface AliPayService {
    String tradeCreate(Long productId);

    String tradeCreate(Long productId, Long paymentAppId);

    String tradeCreateByOrderNo(String orderNo);

    void processOrder(Map<String, String> params);

    void cancelOrder(String orderNo);

    String queryOrder(String orderNo);

    void checkOrderStatus(String orderNo);

    void executeRefund(RefundInfo refundInfo);

    String queryRefund(String refundNo);

    RefundStatusSyncResult queryRefundStatusForSync(String refundNo);

    String queryBill(String billDate, String type);

}
