package cc.ivera.refund.application;

import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.model.RefundInfo;

import java.util.Collection;
import java.util.List;

/**
 * 渠道退款流水应用服务（V1 t_refund_info）：退款申请/状态 CAS 推进/渠道同步/审核。
 */
public interface RefundInfoService {

    RefundInfo createRefundApplication(String orderNo, Integer refundAmount, String reason);

    void updateRefundToProcessing(String refundNo, String contentReturn);

    void updateRefundToSuccess(String refundNo, String refundId, String content);

    void updateRefundToFailed(String refundNo, String content);

    /**
     * 按渠道退款发起结果推进本地退款状态（成功/处理中/失败），渠道调用方只负责编排。
     */
    void applyChannelRefundResult(String refundNo, ChannelRefundResult result);

    boolean updateRefundIfStatusIn(String refundNo,
                                   String refundId,
                                   RefundStatus targetStatus,
                                   String contentReturn,
                                   String contentNotify,
                                   Collection<RefundStatus> currentStatuses);

    boolean syncRefundStatus(RefundStatusSyncResult syncResult);

    RefundInfo repairRefundFromChannel(RefundStatusSyncResult syncResult);

    RefundInfo getByRefundNo(String refundNo);

    List<RefundInfo> listByOrderNo(String orderNo);

    /**
     * 全量渠道退款流水按创建时间倒序（管理端列表）。
     */
    List<RefundInfo> listAll();

    void markApprovalPassed(String refundNo, String approveRemark);

    void markApprovalRejected(String refundNo, String approveRemark);

}
