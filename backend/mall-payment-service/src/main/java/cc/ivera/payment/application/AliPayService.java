package cc.ivera.payment.application;

import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;

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

    ChannelRefundResult executeRefund(ChannelRefundCommand command);

    ChannelRefundStatus queryRefund(ChannelRefundQuery query);

    ChannelRefundSyncResult queryRefundStatusForSync(ChannelRefundQuery query);

    String queryBill(String billDate, String type);

}
