package cc.ivera.refund.infrastructure.persistence.repository;

import cc.ivera.refund.domain.enums.RefundApprovalStatus;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import cc.ivera.refund.infrastructure.persistence.converter.RefundInfoPOConverter;
import cc.ivera.refund.infrastructure.persistence.mapper.RefundInfoMapper;
import cc.ivera.refund.infrastructure.persistence.po.RefundInfoPO;
import cc.ivera.shared.domain.lock.FenceTokenHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 渠道退款流水仓储实现：CAS 条件更新/方言限量扫描全部收口于此。
 */
@Repository
public class RefundInfoRepositoryImpl implements RefundInfoRepository {

    private final RefundInfoMapper refundInfoMapper;

    public RefundInfoRepositoryImpl(RefundInfoMapper refundInfoMapper) {
        this.refundInfoMapper = refundInfoMapper;
    }

    @Override
    public void save(RefundInfo refundInfo) {
        RefundInfoPO po = RefundInfoPOConverter.toPO(refundInfo);
        po.setFenceToken(FenceTokenHolder.get());
        refundInfoMapper.insert(po);
        refundInfo.setId(po.getId());
        refundInfo.setCreateTime(po.getCreateTime());
        refundInfo.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public RefundInfo findByRefundNo(String refundNo) {
        return RefundInfoPOConverter.toDomain(refundInfoMapper.selectOne(new LambdaQueryWrapper<RefundInfoPO>()
            .eq(RefundInfoPO::getRefundNo, refundNo)));
    }

    @Override
    public List<RefundInfo> listByOrderNoCreateTimeDesc(String orderNo) {
        return refundInfoMapper.selectList(new LambdaQueryWrapper<RefundInfoPO>()
                .eq(RefundInfoPO::getOrderNo, orderNo)
                .orderByDesc(RefundInfoPO::getCreateTime))
            .stream().map(RefundInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RefundInfo> listAllCreateTimeDesc() {
        return refundInfoMapper.selectList(new LambdaQueryWrapper<RefundInfoPO>()
                .orderByDesc(RefundInfoPO::getCreateTime))
            .stream().map(RefundInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean updateStatusIfCurrentIn(String refundNo,
                                           String refundId,
                                           String targetStatusType,
                                           String contentReturn,
                                           String contentNotify,
                                           Collection<String> currentStatusTypes) {
        LambdaUpdateWrapper<RefundInfoPO> wrapper = new LambdaUpdateWrapper<RefundInfoPO>()
            .eq(RefundInfoPO::getRefundNo, refundNo)
            .in(RefundInfoPO::getRefundStatus, currentStatusTypes)
            .set(RefundInfoPO::getRefundStatus, targetStatusType);
        // 与原 MP 实体更新（NOT_NULL 策略）一致：仅非空字段覆盖。
        if (refundId != null) {
            wrapper.set(RefundInfoPO::getRefundId, refundId);
        }
        if (contentReturn != null) {
            wrapper.set(RefundInfoPO::getContentReturn, contentReturn);
        }
        if (contentNotify != null) {
            wrapper.set(RefundInfoPO::getContentNotify, contentNotify);
        }
        applyFence(wrapper);
        return refundInfoMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean casApprovalPassed(String refundNo, String approveRemark, Date approvedTime) {
        LambdaUpdateWrapper<RefundInfoPO> wrapper = new LambdaUpdateWrapper<RefundInfoPO>()
            .eq(RefundInfoPO::getRefundNo, refundNo)
            .eq(RefundInfoPO::getApprovalStatus, RefundApprovalStatus.PENDING.getType())
            .in(RefundInfoPO::getRefundStatus,
                RefundStatus.CREATED.getType(),
                RefundStatus.FAILED.getType(),
                RefundStatus.ABNORMAL.getType())
            .set(RefundInfoPO::getApprovalStatus, RefundApprovalStatus.APPROVED.getType())
            .set(RefundInfoPO::getApproveRemark, approveRemark)
            .set(RefundInfoPO::getApprovedTime, approvedTime);
        applyFence(wrapper);
        return refundInfoMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean casApprovalRejected(String refundNo, String approveRemark, Date approvedTime) {
        LambdaUpdateWrapper<RefundInfoPO> wrapper = new LambdaUpdateWrapper<RefundInfoPO>()
            .eq(RefundInfoPO::getRefundNo, refundNo)
            .eq(RefundInfoPO::getApprovalStatus, RefundApprovalStatus.PENDING.getType())
            .set(RefundInfoPO::getApprovalStatus, RefundApprovalStatus.REJECTED.getType())
            .set(RefundInfoPO::getApproveRemark, approveRemark)
            .set(RefundInfoPO::getApprovedTime, approvedTime)
            .set(RefundInfoPO::getRefundStatus, RefundStatus.CLOSED.getType());
        applyFence(wrapper);
        return refundInfoMapper.update(null, wrapper) > 0;
    }

    @Override
    public void updateByRefundNo(RefundInfo patch) {
        RefundInfoPO po = RefundInfoPOConverter.toPO(patch);
        String refundNo = patch.getRefundNo();
        // refundNo 仅作 WHERE 条件，不进 SET（与原实体更新一致：补丁对象不回流退款单号）。
        po.setRefundNo(null);
        LambdaUpdateWrapper<RefundInfoPO> wrapper = new LambdaUpdateWrapper<RefundInfoPO>()
            .eq(RefundInfoPO::getRefundNo, refundNo);
        applyFence(wrapper);
        refundInfoMapper.update(po, wrapper);
    }

    /**
     * 持锁时追加围栏 CAS：SET fence_token = newToken 与 WHERE fence_token &lt;= newToken。
     * 同一把锁内允许连续多次推进；未持锁时跳过，不影响无锁场景。
     */
    private static void applyFence(LambdaUpdateWrapper<RefundInfoPO> wrapper) {
        Long fenceToken = FenceTokenHolder.get();
        if (fenceToken != null) {
            wrapper.le(RefundInfoPO::getFenceToken, fenceToken);
            wrapper.set(RefundInfoPO::getFenceToken, fenceToken);
        }
    }

    @Override
    public Integer sumRefundAmountByOrderNoAndStatuses(String orderNo, Collection<String> statusTypes) {
        Integer amount = refundInfoMapper.sumRefundAmountByOrderNoAndStatuses(orderNo, statusTypes);
        return amount == null ? 0 : amount;
    }

    @Override
    public List<RefundInfo> listProcessingApproved() {
        // 移除 DM8 LIMIT 兼容性 catch 回退：DM8 原生支持 LIMIT，catch 一旦触发会跑不带 LIMIT 的全表扫描
        LambdaQueryWrapper<RefundInfoPO> q = new LambdaQueryWrapper<RefundInfoPO>()
            .eq(RefundInfoPO::getApprovalStatus, RefundApprovalStatus.APPROVED.getType())
            .eq(RefundInfoPO::getRefundStatus, RefundStatus.PROCESSING.getType())
            .orderByAsc(RefundInfoPO::getCreateTime)
            .last("limit 100");
        return refundInfoMapper.selectList(q).stream()
            .map(RefundInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RefundInfo> listByRefundNos(Collection<String> refundNos) {
        if (refundNos == null || refundNos.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return refundInfoMapper.selectList(new LambdaQueryWrapper<RefundInfoPO>()
                .in(RefundInfoPO::getRefundNo, refundNos))
            .stream().map(RefundInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RefundInfo> listByStatusAndCreateTimeRange(String refundStatus, Date start, Date end) {
        return refundInfoMapper.selectList(new LambdaQueryWrapper<RefundInfoPO>()
                .eq(RefundInfoPO::getRefundStatus, refundStatus)
                .ge(RefundInfoPO::getCreateTime, start)
                .lt(RefundInfoPO::getCreateTime, end))
            .stream().map(RefundInfoPOConverter::toDomain).collect(Collectors.toList());
    }
}
