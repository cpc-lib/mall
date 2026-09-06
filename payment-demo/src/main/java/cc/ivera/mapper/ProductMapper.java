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

    /** 提交预占：仅 locked-=qty（支付成功）。 */
    int commitReservedStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 退款回补：available+=qty。 */
    int restock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
