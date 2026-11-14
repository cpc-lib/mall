package cc.ivera.cart.domain.model;

import lombok.Data;

/**
 * 购物车条目：以 userId + productId 为唯一键，数量与勾选状态持久化在 t_cart_item。
 */
@Data
public class CartItem {
    private Long productId;
    private Integer quantity;
    private Boolean selected;
}
