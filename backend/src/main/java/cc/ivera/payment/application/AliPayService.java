package cc.ivera.payment.application;

import cc.ivera.payment.interfaces.vo.ChannelOrderQueryVO;
import cc.ivera.refund.application.RefundStatusSyncResult;
import cc.ivera.refund.domain.model.RefundInfo;

import java.util.Map;

public interface AliPayService extends ChannelPaymentQueryHandler {
    String tradeCreate(Long productId);

    String tradeCreate(Long productId, Long paymentAppId);

    String tradeCreateByOrderNo(String orderNo);

    void processOrder(Map<String, String> params);

    void cancelOrder(String orderNo);

    String queryOrder(String orderNo);

    void checkOrderStatus(String orderNo);

    /**
     * 管理端主动查单：查询渠道交易状态并同步成功状态，不自动关单。
     */
    ChannelOrderQueryVO queryAndSyncStatus(String orderNo);

    void executeRefund(RefundInfo refundInfo);

    String queryRefund(String refundNo);

    RefundStatusSyncResult queryRefundStatusForSync(String refundNo);

    String queryBill(String billDate, String type);

}
