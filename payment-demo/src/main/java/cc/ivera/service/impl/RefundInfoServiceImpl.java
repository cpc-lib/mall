package cc.ivera.service.impl;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.RefundInfo;
import cc.ivera.event.RefundSucceededEvent;
import cc.ivera.event.RefundQuotaReleasedEvent;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.RefundApprovalStatus;
import cc.ivera.enums.RefundStatus;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.RefundInfoMapper;
import cc.ivera.service.OrderInfoService;
import cc.ivera.service.RefundInfoService;
import cc.ivera.service.refund.OrderRefundStatusService;
import cc.ivera.service.refund.RefundStatusSyncResult;
import cc.ivera.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RefundInfoServiceImpl extends ServiceImpl<RefundInfoMapper, RefundInfo> implements RefundInfoService {

    private final OrderInfoService orderInfoService;

    private final OrderRefundStatusService orderRefundStatusService;

    private final ApplicationEventPublisher eventPublisher;

    public RefundInfoServiceImpl(
        OrderInfoService orderInfoService,
        OrderRefundStatusService orderRefundStatusService,
        ApplicationEventPublisher eventPublisher
    ) {
        this.orderInfoService = orderInfoService;
        this.orderRefundStatusService = orderRefundStatusService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundInfo createRefundApplication(String orderNo, Integer refundAmount, String reason) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BizException("订单号不能为空");
        }

        OrderInfo orderInfo = orderInfoService.getOrderByOrderNoForUpdate(orderNo);
        if (orderInfo == null) {
            throw new BizException("订单不存在");
        }
        if (orderInfo.getTotalFee() == null || orderInfo.getTotalFee() <= 0) {
            throw new BizException("订单金额非法");
        }

        String orderStatus = orderInfo.getLegacyStatus();
        boolean refundable = OrderStatus.SUCCESS.getType().equals(orderStatus)
                || OrderStatus.PARTIAL_REFUND.getType().equals(orderStatus)
                || OrderStatus.REFUND_PROCESSING.getType().equals(orderStatus);
        if (!refundable) {
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
            baseMapper.insert(refundInfo);
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

        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_no", refundNo)
                .in("refund_status", currentStatusTypes);

        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setRefundId(refundId);
        refundInfo.setRefundStatus(targetStatus.getType());
        if (contentReturn != null) {
            refundInfo.setContentReturn(contentReturn);
        }
        if (contentNotify != null) {
            refundInfo.setContentNotify(contentNotify);
        }

        boolean updated = baseMapper.update(refundInfo, queryWrapper) > 0;
        if (updated) {
            orderRefundStatusService.refreshOrderRefundStatusByRefundNo(refundNo);
            if (RefundStatus.SUCCESS == targetStatus) {
                eventPublisher.publishEvent(new RefundSucceededEvent(refundNo));
            } else if (RefundStatus.FAILED == targetStatus || RefundStatus.CLOSED == targetStatus) {
                eventPublisher.publishEvent(new RefundQuotaReleasedEvent(refundNo));
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
        // 这里先对退款单行加 for update，保证同一 refundNo 在本地串行处理。
        RefundInfo lockedRefundInfo = getByRefundNoForUpdate(syncResult.getRefundNo());
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

        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_no", syncResult.getRefundNo());

        RefundInfo update = new RefundInfo();
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
            baseMapper.update(update, queryWrapper);
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
            baseMapper.insert(refundInfo);
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
        List<RefundInfo> refundInfoList = listByOrderNo(orderNo);
        int total = 0;
        for (RefundInfo refundInfo : refundInfoList) {
            if (refundInfo == null || refundInfo.getRefund() == null) {
                continue;
            }
            boolean reserved = RefundApprovalStatus.PENDING.getType().equals(refundInfo.getApprovalStatus())
                    || (RefundApprovalStatus.APPROVED.getType().equals(refundInfo.getApprovalStatus())
                    && !RefundStatus.FAILED.getType().equals(refundInfo.getRefundStatus())
                    && !RefundStatus.CLOSED.getType().equals(refundInfo.getRefundStatus()));
            if (reserved) {
                total += refundInfo.getRefund();
            }
        }
        return total;
    }

    @Override
    public RefundInfo getByRefundNo(String refundNo) {
        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_no", refundNo);
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public RefundInfo getByRefundNoForUpdate(String refundNo) {
        if (refundNo == null || refundNo.trim().isEmpty()) {
            return null;
        }
        return baseMapper.selectByRefundNoForUpdate(refundNo);
    }

    @Override
    public List<RefundInfo> listByOrderNo(String orderNo) {
        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no", orderNo).orderByDesc("create_time");
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markApprovalPassed(String refundNo, String approveRemark) {
        RefundInfo refundInfo = getByRefundNo(refundNo);
        if (refundInfo == null) {
            throw new BizException("退款申请单不存在");
        }

        UpdateWrapper<RefundInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("refund_no", refundNo)
                .eq("approval_status", RefundApprovalStatus.PENDING.getType())
                .in("refund_status", Arrays.asList(
                        RefundStatus.CREATED.getType(),
                        RefundStatus.FAILED.getType(),
                        RefundStatus.ABNORMAL.getType()
                ));

        RefundInfo update = new RefundInfo();
        update.setApprovalStatus(RefundApprovalStatus.APPROVED.getType());
        update.setApproveRemark((approveRemark == null || approveRemark.trim().isEmpty()) ? "审核通过" : approveRemark.trim());
        update.setApprovedTime(new Date());

        boolean updated = baseMapper.update(update, updateWrapper) > 0;
        if (updated) {
            return;
        }

        RefundInfo latest = getByRefundNo(refundNo);
        if (latest == null) {
            throw new BizException("退款申请单不存在");
        }
        if (RefundApprovalStatus.APPROVED.getType().equals(latest.getApprovalStatus())) {
            return;
        }
        if (RefundApprovalStatus.REJECTED.getType().equals(latest.getApprovalStatus())) {
            throw new BizException("退款申请单已拒绝，不能再通过");
        }
        if (RefundStatus.SUCCESS.getType().equals(latest.getRefundStatus())) {
            throw new BizException("该退款申请单已退款成功，请勿重复处理");
        }
        if (RefundStatus.PROCESSING.getType().equals(latest.getRefundStatus())) {
            throw new BizException("该退款申请单已在退款处理中，请勿重复处理");
        }
        throw new BizException("退款申请单状态已变化，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markApprovalRejected(String refundNo, String approveRemark) {
        RefundInfo refundInfo = getByRefundNo(refundNo);
        if (refundInfo == null) {
            throw new BizException("退款申请单不存在");
        }

        UpdateWrapper<RefundInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("refund_no", refundNo)
                .eq("approval_status", RefundApprovalStatus.PENDING.getType());

        RefundInfo update = new RefundInfo();
        update.setApprovalStatus(RefundApprovalStatus.REJECTED.getType());
        update.setApproveRemark((approveRemark == null || approveRemark.trim().isEmpty()) ? "审核拒绝" : approveRemark.trim());
        update.setApprovedTime(new Date());
        update.setRefundStatus(RefundStatus.CLOSED.getType());

        boolean updated = baseMapper.update(update, updateWrapper) > 0;
        if (updated) {
            return;
        }

        RefundInfo latest = getByRefundNo(refundNo);
        if (latest == null) {
            throw new BizException("退款申请单不存在");
        }
        if (RefundApprovalStatus.REJECTED.getType().equals(latest.getApprovalStatus())) {
            return;
        }
        if (RefundApprovalStatus.APPROVED.getType().equals(latest.getApprovalStatus())) {
            throw new BizException("退款申请单已审核通过，不能再拒绝");
        }
        throw new BizException("退款申请单状态已变化，请刷新后重试");
    }

    private void updateRefund(String refundNo,
                              String refundId,
                              String refundStatus,
                              String contentReturn,
                              String contentNotify) {
        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_no", refundNo);

        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setRefundId(refundId);
        refundInfo.setRefundStatus(refundStatus);
        if (contentReturn != null) {
            refundInfo.setContentReturn(contentReturn);
        }
        if (contentNotify != null) {
            refundInfo.setContentNotify(contentNotify);
        }
        baseMapper.update(refundInfo, queryWrapper);

        RefundInfo latestRefundInfo = baseMapper.selectOne(queryWrapper);
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
        if (RefundStatus.SUCCESS.equals(targetStatus)) {
            return Arrays.asList(
                    RefundStatus.CREATED,
                    RefundStatus.PROCESSING,
                    RefundStatus.FAILED,
                    RefundStatus.ABNORMAL);
        }
        if (RefundStatus.PROCESSING.equals(targetStatus)) {
            return Arrays.asList(
                    RefundStatus.CREATED,
                    RefundStatus.FAILED);
        }
        if (RefundStatus.ABNORMAL.equals(targetStatus)) {
            return Arrays.asList(
                    RefundStatus.CREATED,
                    RefundStatus.PROCESSING,
                    RefundStatus.FAILED);
        }
        if (RefundStatus.CLOSED.equals(targetStatus)) {
            return Arrays.asList(
                    RefundStatus.CREATED,
                    RefundStatus.PROCESSING,
                    RefundStatus.FAILED,
                    RefundStatus.ABNORMAL);
        }
        if (RefundStatus.FAILED.equals(targetStatus)) {
            return Arrays.asList(
                    RefundStatus.CREATED,
                    RefundStatus.PROCESSING);
        }
        return Collections.emptyList();
    }
}
