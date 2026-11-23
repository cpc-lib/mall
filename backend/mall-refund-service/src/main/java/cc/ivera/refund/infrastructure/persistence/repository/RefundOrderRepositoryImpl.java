package cc.ivera.refund.infrastructure.persistence.repository;

import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.refund.infrastructure.persistence.converter.RefundOrderPOConverter;
import cc.ivera.refund.infrastructure.persistence.mapper.RefundOrderMapper;
import cc.ivera.refund.infrastructure.persistence.po.RefundOrderPO;
import cc.ivera.shared.domain.lock.FenceTokenHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 退款单聚合仓储实现：MP Wrapper/行锁查询全部收口于此，领域层只见端口。
 */
@Repository
public class RefundOrderRepositoryImpl implements RefundOrderRepository {

    private final RefundOrderMapper refundOrderMapper;

    public RefundOrderRepositoryImpl(RefundOrderMapper refundOrderMapper) {
        this.refundOrderMapper = refundOrderMapper;
    }

    @Override
    public void save(RefundOrder refundOrder) {
        RefundOrderPO po = RefundOrderPOConverter.toPO(refundOrder);
        po.setFenceToken(FenceTokenHolder.get());
        refundOrderMapper.insert(po);
        refundOrder.setId(po.getId());
        refundOrder.setCreateTime(po.getCreateTime());
        refundOrder.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(RefundOrder refundOrder) {
        RefundOrderPO po = RefundOrderPOConverter.toPO(refundOrder);
        LambdaUpdateWrapper<RefundOrderPO> wrapper = new LambdaUpdateWrapper<RefundOrderPO>()
            .eq(RefundOrderPO::getId, po.getId());
        Long fenceToken = FenceTokenHolder.get();
        if (fenceToken != null) {
            // 使用 <=：同一把锁内会连续推进多次（创建→定价→受理→发起渠道退款），
            // 首次 UPDATE 已把行令牌写成当前令牌；若用严格 <，同锁内后续 UPDATE 会静默失效。
            // 陈旧持有者令牌更小，仍会被该条件拒绝。
            wrapper.le(RefundOrderPO::getFenceToken, fenceToken);
            po.setFenceToken(fenceToken);
        }
        refundOrderMapper.update(po, wrapper);
    }

    @Override
    public RefundOrder findByRefundNo(String refundNo) {
        return RefundOrderPOConverter.toDomain(refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrderPO>()
            .eq(RefundOrderPO::getRefundNo, refundNo)));
    }

    @Override
    public RefundOrder findByOrderNoAndApplyType(String orderNo, String applyType) {
        return RefundOrderPOConverter.toDomain(refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrderPO>()
            .eq(RefundOrderPO::getOrderNo, orderNo)
            .eq(RefundOrderPO::getApplyType, applyType)));
    }

    @Override
    public RefundOrder findByPaymentNoAndApplyType(String paymentNo, String applyType) {
        return RefundOrderPOConverter.toDomain(refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrderPO>()
            .eq(RefundOrderPO::getPaymentNo, paymentNo)
            .eq(RefundOrderPO::getApplyType, applyType)));
    }

    @Override
    public List<RefundOrder> listByOrderNoCreateTimeDesc(String orderNo) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getOrderNo, orderNo)
                .orderByDesc(RefundOrderPO::getCreateTime))
            .stream().map(RefundOrderPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RefundOrder> listByUserIdPage(Long userId, int limit, long offset) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getUserId, userId)
                .orderByDesc(RefundOrderPO::getCreateTime)
                .orderByDesc(RefundOrderPO::getId)
                .last("LIMIT " + limit + " OFFSET " + offset))
            .stream().map(RefundOrderPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByUserId(Long userId) {
        Long total = refundOrderMapper.selectCount(new LambdaQueryWrapper<RefundOrderPO>()
            .eq(RefundOrderPO::getUserId, userId));
        return total == null ? 0L : total;
    }

    @Override
    public long countAll() {
        Long total = refundOrderMapper.countAllRefundOrders();
        return total == null ? 0L : total;
    }

    @Override
    public List<RefundOrder> listPageCreateTimeDesc(int limit, long offset) {
        return refundOrderMapper.selectPageCreateTimeDesc(limit, offset).stream()
            .map(RefundOrderPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RefundOrder> listByRefundNos(Collection<String> refundNos) {
        if (refundNos == null || refundNos.isEmpty()) {
            return Collections.emptyList();
        }
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                .in(RefundOrderPO::getRefundNo, refundNos)
                .orderByAsc(RefundOrderPO::getId))
            .stream().map(RefundOrderPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RefundOrder> listSuccessBySuccessTimeRange(Date startInclusive, Date endExclusive) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getStatus, RefundOrderStatus.SUCCESS.getType())
                .ge(RefundOrderPO::getSuccessTime, startInclusive)
                .lt(RefundOrderPO::getSuccessTime, endExclusive)
                .orderByAsc(RefundOrderPO::getSuccessTime)
                .orderByAsc(RefundOrderPO::getId))
            .stream().map(RefundOrderPOConverter::toDomain).collect(Collectors.toList());
    }
}
