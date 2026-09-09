package cc.ivera.refund.infrastructure.persistence.mapper;

import cc.ivera.refund.infrastructure.persistence.po.RefundItemPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundItemMapper extends BaseMapper<RefundItemPO> {
    Integer sumOccupiedQuantity(@Param("orderItemId") Long orderItemId, @Param("excludeRefundNo") String excludeRefundNo);

    Integer sumSuccessfulQuantity(@Param("orderItemId") Long orderItemId, @Param("excludeRefundNo") String excludeRefundNo);
}
