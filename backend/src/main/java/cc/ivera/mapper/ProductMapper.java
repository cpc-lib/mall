package cc.ivera.mapper;

import cc.ivera.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface ProductMapper extends BaseMapper<Product> {

    Product selectByIdForUpdate(@Param("id") Long id);

    int deductStockIfEnough(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 下单预占：available-=qty, locked+=qty WHERE available>=qty（防超卖根闸门）。 */
    int reserveStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 释放预占：available+=qty, locked-=qty WHERE locked>=qty。 */
    int releaseReservedStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 确认收货结转已售：locked-=qty, sold+=qty WHERE locked>=qty。 */
    int commitSoldStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 已售退款回补：available+=qty, sold-=qty WHERE sold>=qty。 */
    int releaseSoldStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
