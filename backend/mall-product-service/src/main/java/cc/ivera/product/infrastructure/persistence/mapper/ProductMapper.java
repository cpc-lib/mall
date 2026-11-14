package cc.ivera.product.infrastructure.persistence.mapper;

import cc.ivera.product.infrastructure.persistence.po.ProductPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper extends BaseMapper<ProductPO> {

    int deductStockIfEnough(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 下单预占：available-=qty, locked+=qty WHERE available>=qty（防超卖根闸门）。
     */
    int reserveStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 释放预占：available+=qty, locked-=qty WHERE locked>=qty。
     */
    int releaseReservedStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 确认收货结转已售：locked-=qty, sold+=qty WHERE locked>=qty。
     */
    int commitSoldStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 已售退款回补：available+=qty, sold-=qty WHERE sold>=qty。
     */
    int releaseSoldStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 仅退款未收货核销货损：locked-=qty, lost+=qty WHERE locked>=qty。
     */
    int writeOffLostStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 仅退款已收货核销货损：sold-=qty, lost+=qty WHERE sold>=qty。
     */
    int writeOffSoldLostStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
