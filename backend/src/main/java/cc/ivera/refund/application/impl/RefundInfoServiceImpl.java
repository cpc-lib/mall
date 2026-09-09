package cc.ivera.refund.application.impl;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.refund.application.OrderRefundStatusService;
import cc.ivera.refund.application.RefundInfoService;
import cc.ivera.refund.application.RefundStatusSyncResult;
import cc.ivera.refund.application.event.RefundQuotaReleasedEvent;
import cc.ivera.refund.application.event.RefundSucceededEvent;
import cc.ivera.refund.domain.enums.RefundApprovalStatus;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RefundInfoServiceImpl implements RefundInfoService {

    private static final Set<String> REFUNDABLE_STATUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        OrderStatus.SUCCESS.getType(), OrderStatus.PARTIAL_REFUND.getType(), OrderStatus.REFUND_PROCESSING.getType()
    )));
    private static final Map<RefundStatus, List<RefundStatus>> SYNCABLE_STATUSES;
    private static final Set<String> RELEASED_STATUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        RefundStatus.FAILED.getType(), RefundStatus.CLOSED.getType()
    )));

    static {
        Map<RefundStatus, List<RefundStatus>> m = new EnumMap<>(RefundStatus.class);
        m.put(RefundStatus.SUCCESS, Arrays.asList(RefundStatus.CREATED, RefundStatus.PROCESSING, RefundStatus.FAILED, RefundStatus.ABNORMAL));
        m.put(RefundStatus.PROCESSING, Arrays.asList(RefundStatus.CREATED, RefundStatus.FAILED));
        m.put(RefundStatus.ABNORMAL, Arrays.asList(RefundStatus.CREATED, RefundStatus.PROCESSING, RefundStatus.FAILED));
        m.put(RefundStatus.CLOSED, Arrays.asList(RefundStatus.CREATED, RefundStatus.PROCESSING, RefundStatus.FAILED, RefundStatus.ABNORMAL));
        m.put(RefundStatus.FAILED, Arrays.asList(RefundStatus.CREATED, RefundStatus.PROCESSING));
        SYNCABLE_STATUSES = Collections.unmodifiableMap(m);
    }

    private final RefundInfoRepository refundInfoRepository;
    private final OrderInfoService orderInfoService;
    private final OrderRefundStatusService orderRefundStatusService;
    private final ApplicationEventPublisher eventPublisher;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Map<RefundStatus, Consumer<String>> statusEventDispatch;

    public RefundInfoServiceImpl(
        RefundInfoRepository refundInfoRepository,
        OrderInfoService orderInfoService,
        OrderRefundStatusService orderRefundStatusService,
        ApplicationEventPublisher eventPublisher,
        DistributedLockTemplate distributedLockTemplate,
        TransactionTemplate transactionTemplate
    ) {
        this.refundInfoRepository = refundInfoRepository;
        this.orderInfoService = orderInfoService;
        this.orderRefundStatusService = orderRefundStatusService;
        this.eventPublisher = eventPublisher;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
        Map<RefundStatus, Consumer<String>> dispatch = new EnumMap<>(RefundStatus.class);
        dispatch.put(RefundStatus.SUCCESS, refundNo -> eventPublisher.publishEvent(new RefundSucceededEvent(refundNo)));
        dispatch.put(RefundStatus.FAILED, refundNo -> eventPublisher.publishEvent(new RefundQuotaReleasedEvent(refundNo)));
        dispatch.put(RefundStatus.CLOSED, refundNo -> eventPublisher.publishEvent(new RefundQuotaReleasedEvent(refundNo)));
        this.statusEventDispatch = Collections.unmodifiableMap(dispatch);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundInfo createRefundApplication(String orderNo, Integer refundAmount, String reason) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BizException("订单号不能为空");
        }
        // 退款申请并发互斥靠 Redis 分布式锁（按订单号串行化），事务在锁内开启，
        // 移除底层 FOR UPDATE 后并发安全由「锁 + DuplicateKeyException 兜底」保障。
        String lockKey = "payment:refund:create:" + orderNo;
        return distributedLockTemplate.execute(lockKey, 5000L, -1L, () ->
            transactionTemplate.execute(status -> doCreateRefundApplication(orderNo, refundAmount, reason))
        );
    }

    private RefundInfo doCreateRefundApplication(String orderNo, Integer refundAmount, String reason) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
        if (orderInfo == null) {
            throw new BizException("订单不存在");
        }
        if (orderInfo.getTotalFee() == null || orderInfo.getTotalFee() <= 0) {
            throw new BizException("订单金额非法");
        }

        String orderStatus = orderInfo.getLegacyStatus();
        if (!REFUNDABLE_STATUSES.contains(orderStatus)) {
            throw new BizException("当前订单状态不允许申请退款：" + orderStatus);
        }

        int reservedRefundAmount = getReservedRefundAmount(orderNo);
        int remainRefundAmount = orderInfo.getTotalFee() - reservedRefundAmount;
        if (remainRefundAmount <= 0) {
            throw new BizException("金额已经全部退还处理");
        }

        int actualRefundAmount = refundAmount == null ? remainRefundAmount : refundAmount;
        if (actualRefundAmount <= 0) {
            throw new BizException("退款金额必须大于0");
        }
        if (actualRefundAmount > remainRefundAmount) {
            throw new BizException("退款申请金额超过可退余额，可退金额为：" + remainRefundAmount + "分");
        }

        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setOrderNo(orderNo);
        refundInfo.setRefundNo(OrderNoUtils.getRefundNo());
        refundInfo.setTotalFee(orderInfo.getTotalFee());
        refundInfo.setRefund(actualRefundAmount);
        refundInfo.setReason((reason == null || reason.trim().isEmpty()) ? "正常退款" : reason.trim());
        refundInfo.setApprovalStatus(RefundApprovalStatus.PENDING.getType());
        refundInfo.setRefundStatus(RefundStatus.CREATED.getType());
        refundInfo.setApproveRemark(null);
        refundInfo.setApprovedTime(null);
        try {
            refundInfoRepository.save(refundInfo);
        } catch (DuplicateKeyException e) {
            throw new BizException("退款申请单重复提交，请勿重复操作", e);
        }

        return refundInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefundToProcessing(String refundNo, String contentReturn) {
        updateRefundIfStatusIn(
            refundNo,
            null,
            RefundStatus.PROCESSING,
            contentReturn,
            null,
            Arrays.asList(RefundStatus.CREATED, RefundStatus.FAILED, RefundStatus.ABNORMAL));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefundToSuccess(String refundNo, String refundId, String content) {
        updateRefundIfStatusIn(
            refundNo,
            refundId,
            RefundStatus.SUCCESS,
            content,
            content,
            Arrays.asList(RefundStatus.CREATED, RefundStatus.PROCESSING, RefundStatus.FAILED, RefundStatus.ABNORMAL));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefundToFailed(String refundNo, String content) {
        updateRefundIfStatusIn(
            refundNo,
            null,
            RefundStatus.FAILED,
            content,
            null,
            Arrays.asList(RefundStatus.CREATED, RefundStatus.PROCESSING));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRefundIfStatusIn(String refundNo,
                                          String refundId,
                                          RefundStatus targetStatus,
                                          String contentReturn,
                                          String contentNotify,
                                          Collection<RefundStatus> currentStatuses) {
        if (refundNo == null || refundNo.trim().isEmpty()) {
            throw new BizException("退款单号不能为空");
        }
        if (targetStatus == null) {
            throw new BizException("目标退款状态不能为空");
        }
        if (currentStatuses == null || currentStatuses.isEmpty()) {
            throw new BizException("当前退款状态不能为空");
        }

        List<String> currentStatusTypes = currentStatuses.stream()
            .map(RefundStatus::getType)
            .collect(Collectors.toList());

        boolean updated = refundInfoRepository.updateStatusIfCurrentIn(
            refundNo, refundId, targetStatus.getType(), contentReturn, contentNotify, currentStatusTypes);
        if (updated) {
            orderRefundStatusService.refreshOrderRefundStatusByRefundNo(refundNo);
            Consumer<String> eventPublisher = statusEventDispatch.get(targetStatus);
            if (eventPublisher != null) {
                eventPublisher.accept(refundNo);
            }
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean syncRefundStatus(RefundStatusSyncResult syncResult) {
        if (syncResult == null) {
            throw new BizException("退款状态同步结果不能为空");
        }
        if (syncResult.getRefundNo() == null || syncResult.getRefundNo().trim().isEmpty()) {
            throw new BizException("退款单号不能为空");
        }
        if (!syncResult.hasRefundStatus()) {
            return false;
        }

        // 退款成功通知、主动查单同步、后台补偿任务都可能并发到达。
        // 普通读即可：状态推进走下方 updateRefundIfStatusIn 的 CAS 条件更新（仅一方成功），
        // 事件也只在 CAS 成功后发布，并发输家自然幂等，无需行锁串行。
        RefundInfo lockedRefundInfo = getByRefundNo(syncResult.getRefundNo());
        if (lockedRefundInfo == null) {
            log.warn("退款状态同步失败，本地退款单不存在，refundNo={}", syncResult.getRefundNo());
            return false;
        }

        validateRefundSyncData(lockedRefundInfo, syncResult);

        if (RefundStatus.SUCCESS.getType().equals(lockedRefundInfo.getRefundStatus())
            && RefundStatus.SUCCESS == syncResult.getRefundStatus()) {
            eventPublisher.publishEvent(new RefundSucceededEvent(syncResult.getRefundNo()));
            return false;
        }

        Collection<RefundStatus> currentStatuses = getSyncableCurrentStatuses(syncResult.getRefundStatus());
        if (currentStatuses.isEmpty()) {
            return false;
        }

        boolean updated = updateRefundIfStatusIn(
            syncResult.getRefundNo(),
            syncResult.getRefundId(),
            syncResult.getRefundStatus(),
            null,
            syncResult.getContent(),
            currentStatuses);
        if (!updated) {
            orderRefundStatusService.refreshOrderRefundStatusByRefundNo(syncResult.getRefundNo());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundInfo repairRefundFromChannel(RefundStatusSyncResult syncResult) {
        if (syncResult == null) {
            throw new BizException("渠道退款数据不能为空");
        }
        if (syncResult.getRefundNo() == null || syncResult.getRefundNo().trim().isEmpty()) {
            throw new BizException("渠道退款单号不能为空");
        }
        if (syncResult.getOrderNo() == null || syncResult.getOrderNo().trim().isEmpty()) {
            throw new BizException("渠道退款数据缺少订单号");
        }

        RefundInfo refundInfo = getByRefundNo(syncResult.getRefundNo());
        if (refundInfo == null) {
            return createRefundFromChannel(syncResult);
        }

        if (refundInfo.getOrderNo() != null && !refundInfo.getOrderNo().equals(syncResult.getOrderNo())) {
            throw new BizException("本地退款单订单号与渠道不一致，refundNo=" + syncResult.getRefundNo());
        }

        RefundInfo update = new RefundInfo();
        update.setRefundNo(syncResult.getRefundNo());
        boolean changed = false;
        if (shouldUpdateString(refundInfo.getOrderNo(), syncResult.getOrderNo())) {
            update.setOrderNo(syncResult.getOrderNo());
            changed = true;
        }
        if (shouldUpdateString(refundInfo.getRefundId(), syncResult.getRefundId())) {
            update.setRefundId(syncResult.getRefundId());
            changed = true;
        }
        if (shouldUpdateInteger(refundInfo.getTotalFee(), syncResult.getTotalFee())) {
            update.setTotalFee(syncResult.getTotalFee());
            changed = true;
        }
        if (shouldUpdateInteger(refundInfo.getRefund(), syncResult.getRefundAmount())) {
            update.setRefund(syncResult.getRefundAmount());
            changed = true;
        }

        if (changed) {
            refundInfoRepository.updateByRefundNo(update);
        }
        return getByRefundNo(syncResult.getRefundNo());
    }

    private RefundInfo createRefundFromChannel(RefundStatusSyncResult syncResult) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(syncResult.getOrderNo());
        if (orderInfo == null) {
            throw new BizException("渠道退款对应订单不存在，orderNo=" + syncResult.getOrderNo());
        }
        if (syncResult.getRefundAmount() == null || syncResult.getRefundAmount() <= 0) {
            throw new BizException("渠道退款缺少退款金额，无法补录退款单");
        }

        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setOrderNo(syncResult.getOrderNo());
        refundInfo.setRefundNo(syncResult.getRefundNo());
        refundInfo.setRefundId(syncResult.getRefundId());
        refundInfo.setTotalFee(syncResult.getTotalFee() == null ? orderInfo.getTotalFee() : syncResult.getTotalFee());
        refundInfo.setRefund(syncResult.getRefundAmount());
        refundInfo.setReason("渠道对账补录");
        refundInfo.setApprovalStatus(RefundApprovalStatus.APPROVED.getType());
        refundInfo.setApproveRemark("渠道对账补录");
        refundInfo.setApprovedTime(new Date());
        refundInfo.setRefundStatus(RefundStatus.CREATED.getType());
        refundInfo.setContentNotify(syncResult.getContent());
        try {
            refundInfoRepository.save(refundInfo);
        } catch (DuplicateKeyException e) {
            RefundInfo exist = getByRefundNo(syncResult.getRefundNo());
            if (exist != null) {
                return exist;
            }
            throw e;
        }
        return refundInfo;
    }

    private int getReservedRefundAmount(String orderNo) {
        int total = 0;
        for (RefundInfo refundInfo : listByOrderNo(orderNo)) {
            if (refundInfo == null || refundInfo.getRefund() == null) {
                continue;
            }
            boolean pending = RefundApprovalStatus.PENDING.getType().equals(refundInfo.getApprovalStatus());
            boolean approvedAndNotReleased = RefundApprovalStatus.APPROVED.getType().equals(refundInfo.getApprovalStatus())
                && !RELEASED_STATUSES.contains(refundInfo.getRefundStatus());
            if (pending || approvedAndNotReleased) {
                total += refundInfo.getRefund();
            }
        }
        return total;
    }

    @Override
    public RefundInfo getByRefundNo(String refundNo) {
        return refundInfoRepository.findByRefundNo(refundNo);
    }

    @Override
    public List<RefundInfo> listByOrderNo(String orderNo) {
        return refundInfoRepository.listByOrderNoCreateTimeDesc(orderNo);
    }

    @Override
    public List<RefundInfo> listAll() {
        return refundInfoRepository.listAllCreateTimeDesc();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markApprovalPassed(String refundNo, String approveRemark) {
        if (refundInfoRepository.casApprovalPassed(refundNo, resolveRemark(approveRemark, "审核通过"), new Date())) {
            return;
        }
        diagnoseApprovalConflict(refundNo, RefundApprovalStatus.APPROVED, this::conflictMessageForPass);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markApprovalRejected(String refundNo, String approveRemark) {
        if (refundInfoRepository.casApprovalRejected(refundNo, resolveRemark(approveRemark, "审核拒绝"), new Date())) {
            return;
        }
        diagnoseApprovalConflict(refundNo, RefundApprovalStatus.REJECTED, this::conflictMessageForReject);
    }

    private String resolveRemark(String input, String fallback) {
        return (input == null || input.trim().isEmpty()) ? fallback : input.trim();
    }

    private void diagnoseApprovalConflict(String refundNo,
                                          RefundApprovalStatus targetApproval,
                                          Function<RefundInfo, String> messageResolver) {
        RefundInfo latest = getByRefundNo(refundNo);
        if (latest == null) {
            throw new BizException("退款申请单不存在");
        }
        if (targetApproval.getType().equals(latest.getApprovalStatus())) {
            return;
        }
        throw new BizException(messageResolver.apply(latest));
    }

    private String conflictMessageForPass(RefundInfo latest) {
        if (RefundApprovalStatus.REJECTED.getType().equals(latest.getApprovalStatus())) {
            return "退款申请单已拒绝，不能再通过";
        }
        if (RefundStatus.SUCCESS.getType().equals(latest.getRefundStatus())) {
            return "该退款申请单已退款成功，请勿重复处理";
        }
        if (RefundStatus.PROCESSING.getType().equals(latest.getRefundStatus())) {
            return "该退款申请单已在退款处理中，请勿重复处理";
        }
        return "退款申请单状态已变化，请刷新后重试";
    }

    private String conflictMessageForReject(RefundInfo latest) {
        if (RefundApprovalStatus.APPROVED.getType().equals(latest.getApprovalStatus())) {
            return "退款申请单已审核通过，不能再拒绝";
        }
        return "退款申请单状态已变化，请刷新后重试";
    }

    /**
     * @deprecated V1 无条件状态更新入口，已被 {@link #updateRefundIfStatusIn} CAS 链路取代；
     * 现状为无调用方的遗留私有方法，按等价搬移保留。
     */
    @Deprecated
    private void updateRefund(String refundNo,
                              String refundId,
                              String refundStatus,
                              String contentReturn,
                              String contentNotify) {
        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setRefundNo(refundNo);
        refundInfo.setRefundId(refundId);
        refundInfo.setRefundStatus(refundStatus);
        if (contentReturn != null) {
            refundInfo.setContentReturn(contentReturn);
        }
        if (contentNotify != null) {
            refundInfo.setContentNotify(contentNotify);
        }
        refundInfoRepository.updateByRefundNo(refundInfo);

        RefundInfo latestRefundInfo = refundInfoRepository.findByRefundNo(refundNo);
        if (latestRefundInfo != null) {
            orderRefundStatusService.refreshOrderRefundStatus(latestRefundInfo.getOrderNo());
        }
    }


    private void validateRefundSyncData(RefundInfo lockedRefundInfo, RefundStatusSyncResult syncResult) {
        if (syncResult.getOrderNo() != null
            && lockedRefundInfo.getOrderNo() != null
            && !syncResult.getOrderNo().equals(lockedRefundInfo.getOrderNo())) {
            throw new BizException("退款通知订单号不一致，refundNo=" + syncResult.getRefundNo());
        }

        if (syncResult.getRefundAmount() != null
            && lockedRefundInfo.getRefund() != null
            && syncResult.getRefundAmount() > 0
            && !syncResult.getRefundAmount().equals(lockedRefundInfo.getRefund())) {
            throw new BizException("退款通知金额不一致，refundNo=" + syncResult.getRefundNo());
        }

        if (syncResult.getTotalFee() != null
            && lockedRefundInfo.getTotalFee() != null
            && syncResult.getTotalFee() > 0
            && !syncResult.getTotalFee().equals(lockedRefundInfo.getTotalFee())) {
            throw new BizException("退款通知原订单金额不一致，refundNo=" + syncResult.getRefundNo());
        }
    }

    private boolean shouldUpdateString(String currentValue, String channelValue) {
        return channelValue != null
            && !channelValue.trim().isEmpty()
            && !channelValue.equals(currentValue);
    }

    private boolean shouldUpdateInteger(Integer currentValue, Integer channelValue) {
        return channelValue != null
            && channelValue > 0
            && !channelValue.equals(currentValue);
    }

    private Collection<RefundStatus> getSyncableCurrentStatuses(RefundStatus targetStatus) {
        return SYNCABLE_STATUSES.getOrDefault(targetStatus, Collections.emptyList());
    }
}
