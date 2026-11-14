package cc.ivera.cart.infrastructure.persistence.mapper;

import cc.ivera.cart.infrastructure.persistence.po.CartItemPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItemPO> {

    /**
     * userId + productId 唯一键上的原子 upsert：并发加购由唯一键与 MERGE 串行化，不丢数量。
     */
    @Insert("MERGE INTO t_cart_item t "
        + "USING (SELECT #{userId} AS user_id, #{productId} AS product_id FROM DUAL) s "
        + "ON (t.user_id = s.user_id AND t.product_id = s.product_id) "
        + "WHEN MATCHED THEN UPDATE SET t.quantity = #{quantity}, t.selected = #{selected}, t.update_time = CURRENT_TIMESTAMP "
        + "WHEN NOT MATCHED THEN INSERT (user_id, product_id, quantity, selected, create_time, update_time) "
        + "VALUES (#{userId}, #{productId}, #{quantity}, #{selected}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int upsert(@Param("userId") Long userId,
               @Param("productId") Long productId,
               @Param("quantity") Integer quantity,
               @Param("selected") String selected);
}
