package cc.ivera.refund.infrastructure.persistence.repository;

import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.refund.infrastructure.persistence.converter.RefundOrderPOConverter;
import cc.ivera.refund.infrastructure.persistence.mapper.RefundOrderMapper;
import cc.ivera.refund.infrastructure.persistence.po.RefundOrderPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        refundOrderMapper.insert(po);
        refundOrder.setId(po.getId());
        refundOrder.setCreateTime(po.getCreateTime());
        refundOrder.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(RefundOrder refundOrder) {
        refundOrderMapper.updateById(RefundOrderPOConverter.toPO(refundOrder));
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
    public List<RefundOrder> listByUserIdCreateTimeDesc(Long userId) {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                .eq(RefundOrderPO::getUserId, userId)
                .orderByDesc(RefundOrderPO::getCreateTime))
            .stream().map(RefundOrderPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RefundOrder> listAllCreateTimeDesc() {
        return refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrderPO>()
                .orderByDesc(RefundOrderPO::getCreateTime))
            .stream().map(RefundOrderPOConverter::toDomain).collect(Collectors.toList());
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
