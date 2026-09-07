package cc.ivera.mapper;

import cc.ivera.entity.OrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface OrderItemMapper extends BaseMapper<OrderItem> {

    int increaseRefundedQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 明细层退款冻结（数量+金额双上限，任一不足 affectedRows=0）。 */
    int freezeItemRefund(@Param("id") Long id, @Param("qty") Integer qty, @Param("amount") Integer amount);

    /** 释放明细层退款冻结（拒绝/撤回，幂等边界内）。 */
    int releaseItemRefundFreeze(@Param("id") Long id, @Param("qty") Integer qty, @Param("amount") Integer amount);

    /** 明细层退款结转：冻结转已退。 */
    int settleItemRefund(@Param("id") Long id, @Param("qty") Integer qty, @Param("amount") Integer amount);

    /** 补库存累计：restocked+qty 不超过已退数量。 */
    int addRestockedQty(@Param("id") Long id, @Param("qty") Integer qty);
}
