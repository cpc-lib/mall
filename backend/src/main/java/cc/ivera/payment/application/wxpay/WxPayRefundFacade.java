package cc.ivera.payment.application.wxpay;

import cc.ivera.refund.application.RefundStatusSyncResult;
import cc.ivera.refund.domain.model.RefundInfo;

import java.util.List;
import java.util.Map;

public interface WxPayRefundFacade {

    void executeRefund(RefundInfo refundInfo);

    String queryRefund(String refundNo);

    RefundStatusSyncResult queryRefundStatusForSync(String refundNo);

    List<RefundStatusSyncResult> queryOrderRefundsForSync(String orderNo);

    void processRefund(Map<String, Object> bodyMap);
}
