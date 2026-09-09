package cc.ivera.refund.application;

import cc.ivera.refund.domain.model.RefundInfo;

import java.util.List;

/**
 * 退款申请应用服务（V1 审核/发起渠道退款/对账链路）。
 */
public interface RefundApplicationService {

    RefundInfo createApplication(String orderNo, Integer refundAmount, String reason);

    void approve(String refundNo, String approveRemark);

    void reject(String refundNo, String approveRemark);

    List<RefundInfo> listAll();

    List<RefundInfo> listByOrderNo(String orderNo);

    RefundInfo queryRefundStatus(String refundNo);

    List<RefundInfo> reconcileOrderRefundStatus(String orderNo);
}
