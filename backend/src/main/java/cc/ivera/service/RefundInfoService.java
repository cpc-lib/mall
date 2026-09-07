package cc.ivera.service;

import cc.ivera.entity.RefundInfo;
import cc.ivera.enums.RefundStatus;
import cc.ivera.service.refund.RefundStatusSyncResult;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;

public interface RefundInfoService extends IService<RefundInfo> {

    RefundInfo createRefundApplication(String orderNo, Integer refundAmount, String reason);

    void updateRefundToProcessing(String refundNo, String contentReturn);

    void updateRefundToSuccess(String refundNo, String refundId, String content);

    void updateRefundToFailed(String refundNo, String content);

    boolean updateRefundIfStatusIn(String refundNo,
                                   String refundId,
                                   RefundStatus targetStatus,
                                   String contentReturn,
                                   String contentNotify,
                                   Collection<RefundStatus> currentStatuses);

    boolean syncRefundStatus(RefundStatusSyncResult syncResult);

    RefundInfo repairRefundFromChannel(RefundStatusSyncResult syncResult);

    RefundInfo getByRefundNo(String refundNo);

    RefundInfo getByRefundNoForUpdate(String refundNo);

    List<RefundInfo> listByOrderNo(String orderNo);

    void markApprovalPassed(String refundNo, String approveRemark);

    void markApprovalRejected(String refundNo, String approveRemark);

}
