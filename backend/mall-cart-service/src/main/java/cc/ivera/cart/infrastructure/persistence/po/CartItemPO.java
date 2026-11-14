package cc.ivera.cart.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 购物车条目：userId + productId 唯一，selected 用 '0'/'1' 存储。
 */
@Data
@TableName("t_cart_item")
public class CartItemPO extends BaseEntity {
    private Long userId;
    private Long productId;
    private Integer quantity;
    /**
     * '0'=未勾选 '1'=已勾选
     */
    private String selected;
}
