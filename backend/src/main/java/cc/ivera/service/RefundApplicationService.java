package cc.ivera.service;

import cc.ivera.entity.RefundInfo;

import java.util.List;

public interface RefundApplicationService {

    RefundInfo createApplication(String orderNo, Integer refundAmount, String reason);

    void approve(String refundNo, String approveRemark);

    void reject(String refundNo, String approveRemark);

    List<RefundInfo> listAll();

    List<RefundInfo> listByOrderNo(String orderNo);

    RefundInfo queryRefundStatus(String refundNo);

    List<RefundInfo> reconcileOrderRefundStatus(String orderNo);
}
