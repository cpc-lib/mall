package cc.ivera.mapper;

import cc.ivera.entity.RefundItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface RefundItemMapper extends BaseMapper<RefundItem> {
    Integer sumOccupiedQuantity(@Param("orderItemId") Long orderItemId, @Param("excludeRefundNo") String excludeRefundNo);
    Integer sumSuccessfulQuantity(@Param("orderItemId") Long orderItemId, @Param("excludeRefundNo") String excludeRefundNo);
}
