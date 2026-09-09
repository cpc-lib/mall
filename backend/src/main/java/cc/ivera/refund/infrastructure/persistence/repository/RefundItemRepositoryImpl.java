package cc.ivera.refund.infrastructure.persistence.repository;

import cc.ivera.refund.domain.model.RefundItem;
import cc.ivera.refund.domain.repository.RefundItemRepository;
import cc.ivera.refund.infrastructure.persistence.converter.RefundItemPOConverter;
import cc.ivera.refund.infrastructure.persistence.mapper.RefundItemMapper;
import cc.ivera.refund.infrastructure.persistence.po.RefundItemPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 退款明细仓储实现。
 */
@Repository
public class RefundItemRepositoryImpl implements RefundItemRepository {

    private final RefundItemMapper refundItemMapper;

    public RefundItemRepositoryImpl(RefundItemMapper refundItemMapper) {
        this.refundItemMapper = refundItemMapper;
    }

    @Override
    public void save(RefundItem item) {
        RefundItemPO po = RefundItemPOConverter.toPO(item);
        refundItemMapper.insert(po);
        item.setId(po.getId());
        item.setCreateTime(po.getCreateTime());
        item.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public List<RefundItem> listByRefundNoAsc(String refundNo) {
        return refundItemMapper.selectList(new LambdaQueryWrapper<RefundItemPO>()
                .eq(RefundItemPO::getRefundNo, refundNo)
                .orderByAsc(RefundItemPO::getId))
            .stream().map(RefundItemPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteByRefundNo(String refundNo) {
        refundItemMapper.delete(new LambdaQueryWrapper<RefundItemPO>()
            .eq(RefundItemPO::getRefundNo, refundNo));
    }
}
