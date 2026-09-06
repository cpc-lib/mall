package cc.ivera.service;

import cc.ivera.entity.RefundInfo;
import cc.ivera.service.refund.RefundStatusSyncResult;
import cc.ivera.vo.ChannelOrderQueryVO;

import java.util.Map;

public interface AliPayService {
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
