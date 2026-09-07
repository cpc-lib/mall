package cc.ivera.service.wxpay;

import cc.ivera.entity.RefundInfo;
import cc.ivera.service.refund.RefundStatusSyncResult;

import java.util.List;
import java.util.Map;

public interface WxPayRefundFacade {

    void executeRefund(RefundInfo refundInfo);

    String queryRefund(String refundNo);

    RefundStatusSyncResult queryRefundStatusForSync(String refundNo);

    List<RefundStatusSyncResult> queryOrderRefundsForSync(String orderNo);

    void processRefund(Map<String, Object> bodyMap);
}
