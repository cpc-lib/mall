package cc.ivera.refund.application.impl;

import cc.ivera.api.order.OrderQueryApi;
import cc.ivera.api.payment.PaymentChannelRefundApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;
import cc.ivera.payment.domain.enums.ChannelRefundState;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.refund.application.*;
import cc.ivera.refund.domain.enums.RefundApprovalStatus;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLock;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.infrastructure.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
public class RefundApplicationServiceImpl implements RefundApplicationService {

    private final RefundInfoService refundInfoService;

    private final OrderQueryApi orderQueryApi;

    private final PaymentChannelRefundApi paymentChannelRefundApi;

    private final OrderRefundStatusService orderRefundStatusService;

    private final DistributedLockTemplate distributedLockTemplate;

    private final RefundStatusSyncMessageService refundStatusSyncMessageService;

    private final TransactionTemplate transactionTemplate;

    public RefundApplicationServiceImpl(
        RefundInfoService refundInfoService,
        OrderQueryApi orderQueryApi,
        PaymentChannelRefundApi paymentChannelRefundApi,
        OrderRefundStatusService orderRefundStatusService,
        DistributedLockTemplate distributedLockTemplate,
        RefundStatusSyncMessageService refundStatusSyncMessageService,
        TransactionTemplate transactionTemplate
    ) {
        this.refundInfoService = refundInfoService;
        this.orderQueryApi = orderQueryApi;
        this.paymentChannelRefundApi = paymentChannelRefundApi;
        this.orderRefundStatusService = orderRefundStatusService;
        this.distributedLockTemplate = distributedLockTemplate;
        this.refundStatusSyncMessageService = refundStatusSyncMessageService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundInfo createApplication(String orderNo, Integer refundAmount, String reason) {
        return refundInfoService.createRefundApplication(orderNo, refundAmount, reason);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DistributedLock(key = "'payment:refund:approve:' + #refundNo", waitTimeMillis = 5000L)
    public void approve(String refundNo, String approveRemark) {
        RefundInfo refundInfo = refundInfoService.getByRefundNo(refundNo);
        if (refundInfo == null) {
            throw new BizException("退款申请单不存在");
        }
        if (RefundApprovalStatus.REJECTED.getType().equals(refundInfo.getApprovalStatus())) {
            throw new BizException("退款申请单已拒绝，不能审核通过");
        }
        if (RefundStatus.SUCCESS.getType().equals(refundInfo.getRefundStatus())) {
            throw new BizException("该退款申请单已退款成功，请勿重复处理");
        }
        if (RefundStatus.PROCESSING.getType().equals(refundInfo.getRefundStatus())) {
            throw new BizException("该退款申请单已在退款处理中，请勿重复处理");
        }

        OrderInfo orderInfo = orderQueryApi.findByOrderNo(refundInfo.getOrderNo());
        if (orderInfo == null) {
            throw new BizException("订单不存在");
        }
        validateSupportedPayType(orderInfo.getPaymentType());

        refundInfoService.markApprovalPassed(refundNo, approveRemark);
        claimRefundForExecution(refundNo);
        RefundInfo latestRefundInfo = refundInfoService.getByRefundNo(refundNo);

        try {
            executeRefund(orderInfo.getPaymentType(), latestRefundInfo);
        } catch (RuntimeException e) {
            markRefundSubmitFailed(refundNo, e);
            throw e;
        }
        refundStatusSyncMessageService.sendRefundStatusSyncMessage(refundNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DistributedLock(key = "'payment:refund:reject:' + #refundNo", waitTimeMillis = 5000L)
    public void reject(String refundNo, String approveRemark) {
        refundInfoService.markApprovalRejected(refundNo, approveRemark);
    }

    @Override
    public List<RefundInfo> listAll() {
        return refundInfoService.listAll();
    }

    @Override
    public List<RefundInfo> listByOrderNo(String orderNo) {
        return refundInfoService.listByOrderNo(orderNo);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DistributedLock(key = "'payment:refund:query:' + #refundNo", waitTimeMillis = 5000L)
    public RefundInfo queryRefundStatus(String refundNo) {
        RefundInfo refundInfo = refundInfoService.getByRefundNo(refundNo);
        if (refundInfo == null) {
            throw new BizException("退款申请单不存在");
        }
        if (!RefundApprovalStatus.APPROVED.getType().equals(refundInfo.getApprovalStatus())) {
            throw new BizException("退款申请尚未提交支付渠道，不能主动查询渠道状态");
        }

        OrderInfo orderInfo = orderQueryApi.findByOrderNo(refundInfo.getOrderNo());
        if (orderInfo == null) {
            throw new BizException("订单不存在");
        }
        validateSupportedPayType(orderInfo.getPaymentType());

        syncRefundByChannel(orderInfo.getPaymentType(), refundInfo);

        return refundInfoService.getByRefundNo(refundNo);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DistributedLock(key = "'payment:refund:reconcile:' + #orderNo", waitTimeMillis = 5000L)
    public List<RefundInfo> reconcileOrderRefundStatus(String orderNo) {
        OrderInfo orderInfo = orderQueryApi.findByOrderNo(orderNo);
        if (orderInfo == null) {
            throw new BizException("订单不存在");
        }
        validateSupportedPayType(orderInfo.getPaymentType());

        Set<String> channelRefundNos = reconcileWxOrderRefundsIfNeeded(orderInfo);
        syncLocalRefundsForOrder(orderInfo, channelRefundNos);
        orderRefundStatusService.refreshOrderRefundStatus(orderNo);
        return refundInfoService.listByOrderNo(orderNo);
    }

    private void claimRefundForExecution(String refundNo) {
        boolean updated = refundInfoService.updateRefundIfStatusIn(
            refundNo,
            null,
            RefundStatus.PROCESSING,
            null,
            null,
            Arrays.asList(RefundStatus.CREATED, RefundStatus.FAILED, RefundStatus.ABNORMAL));
        if (!updated) {
            throw new BizException("该退款申请单状态已变化，请勿重复处理");
        }
    }

    private void executeRefund(String paymentType, RefundInfo refundInfo) {
        ChannelRefundCommand command = new ChannelRefundCommand();
        command.setRefundNo(refundInfo.getRefundNo());
        command.setOrderNo(refundInfo.getOrderNo());
        command.setChannel(paymentType);
        command.setTotalFee(refundInfo.getTotalFee());
        command.setRefundAmount(refundInfo.getRefund());
        command.setReason(refundInfo.getReason());

        ChannelRefundResult result = paymentChannelRefundApi.executeRefund(command);
        refundInfoService.applyChannelRefundResult(refundInfo.getRefundNo(), result);
        if (result == null || result.getOutcome() == ChannelRefundResult.Outcome.FAILED) {
            throw new BizException(result == null ? "渠道退款结果为空" : result.getErrorMessage());
        }
    }

    private void validateSupportedPayType(String paymentType) {
        if (!PayType.WXPAY.getType().equals(paymentType)
            && !PayType.ALIPAY.getType().equals(paymentType)) {
            throw new BizException("不支持的支付方式：" + paymentType);
        }
    }

    private void markRefundSubmitFailed(String refundNo, Exception exception) {
        String message = exception.getMessage() == null ? "退款提交失败" : exception.getMessage();
        refundInfoService.updateRefundIfStatusIn(
            refundNo,
            null,
            RefundStatus.FAILED,
            JsonUtils.toJson(Collections.singletonMap("message", message)),
            null,
            Arrays.asList(RefundStatus.CREATED, RefundStatus.PROCESSING));
    }

    private RefundStatusSyncResult queryRefundStatusFromChannel(String paymentType, RefundInfo refundInfo) {
        ChannelRefundQuery query = new ChannelRefundQuery(refundInfo.getRefundNo(), refundInfo.getOrderNo(), paymentType);
        ChannelRefundSyncResult result = paymentChannelRefundApi.queryRefundStatusForSync(query);
        return toRefundStatusSyncResult(result);
    }

    private Set<String> reconcileWxOrderRefundsIfNeeded(OrderInfo orderInfo) {
        Set<String> channelRefundNos = new HashSet<>();
        if (!PayType.WXPAY.getType().equals(orderInfo.getPaymentType())) {
            return channelRefundNos;
        }

        List<ChannelRefundSyncResult> channelResults =
            paymentChannelRefundApi.queryOrderRefundsForSync(orderInfo.getOrderNo(), orderInfo.getPaymentType());
        for (ChannelRefundSyncResult channelResult : channelResults) {
            RefundStatusSyncResult syncResult = toRefundStatusSyncResult(channelResult);
            validateChannelOrderResult(orderInfo, syncResult);
            refundInfoService.repairRefundFromChannel(syncResult);
            refundInfoService.syncRefundStatus(syncResult);
            if (syncResult.getRefundNo() != null) {
                channelRefundNos.add(syncResult.getRefundNo());
            }
        }
        return channelRefundNos;
    }

    @Override
    public void applyRefundSync(ChannelRefundSyncResult result) {
        if (result == null || result.getRefundNo() == null || result.getRefundNo().trim().isEmpty()) {
            return;
        }
        distributedLockTemplate.execute("refund:order:" + result.getRefundNo(), 5000L, () -> {
            RefundStatusSyncResult syncResult = toRefundStatusSyncResult(result);
            transactionTemplate.execute(status -> {
                refundInfoService.syncRefundStatus(syncResult);
                return null;
            });
            return null;
        });
    }

    private void syncLocalRefundsForOrder(OrderInfo orderInfo, Set<String> channelRefundNos) {
        List<RefundInfo> refundInfoList = refundInfoService.listByOrderNo(orderInfo.getOrderNo());
        for (RefundInfo refundInfo : refundInfoList) {
            if (!shouldSyncLocalRefund(refundInfo)) {
                continue;
            }
            if (channelRefundNos.contains(refundInfo.getRefundNo())) {
                continue;
            }
            syncRefundByChannel(orderInfo.getPaymentType(), refundInfo);
        }
    }

    private void syncRefundByChannel(String paymentType, RefundInfo refundInfo) {
        RefundStatusSyncResult syncResult = queryRefundStatusFromChannel(paymentType, refundInfo);
        validateChannelResult(refundInfo, syncResult);
        refundInfoService.repairRefundFromChannel(syncResult);
        refundInfoService.syncRefundStatus(syncResult);
    }

    private RefundStatusSyncResult toRefundStatusSyncResult(ChannelRefundSyncResult result) {
        if (result == null) {
            return null;
        }
        return RefundStatusSyncResult.of(
            result.getOrderNo(),
            result.getRefundNo(),
            result.getChannelRefundId(),
            result.getChannelStatus(),
            mapChannelRefundStatus(result.getState()),
            result.getContent(),
            result.getTotalFee(),
            result.getRefundAmount());
    }

    private RefundStatus mapChannelRefundStatus(ChannelRefundState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case SUCCESS -> RefundStatus.SUCCESS;
            case PROCESSING -> RefundStatus.PROCESSING;
            case ABNORMAL -> RefundStatus.ABNORMAL;
            case CLOSED -> RefundStatus.CLOSED;
            case UNKNOWN -> null;
        };
    }

    private boolean shouldSyncLocalRefund(RefundInfo refundInfo) {
        return refundInfo != null
            && refundInfo.getRefundNo() != null
            && !refundInfo.getRefundNo().trim().isEmpty()
            && RefundApprovalStatus.APPROVED.getType().equals(refundInfo.getApprovalStatus());
    }

    private void validateChannelOrderResult(OrderInfo orderInfo, RefundStatusSyncResult syncResult) {
        if (syncResult == null) {
            throw new BizException("退款查询结果不能为空");
        }
        if (syncResult.getOrderNo() != null && !orderInfo.getOrderNo().equals(syncResult.getOrderNo())) {
            throw new BizException("渠道退款查询结果与本地订单不一致");
        }
    }

    private void validateChannelResult(RefundInfo refundInfo, RefundStatusSyncResult syncResult) {
        if (syncResult == null) {
            throw new BizException("退款查询结果不能为空");
        }
        if (syncResult.getRefundNo() != null && !refundInfo.getRefundNo().equals(syncResult.getRefundNo())) {
            throw new BizException("渠道退款查询结果与本地退款单不一致");
        }
        if (syncResult.getOrderNo() != null && !refundInfo.getOrderNo().equals(syncResult.getOrderNo())) {
            throw new BizException("渠道退款查询结果与本地订单不一致");
        }
    }
}
