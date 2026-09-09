package cc.ivera.cart.domain.model;

import lombok.Data;

/**
 * 购物车条目（Redis Hash 存储，field=productId，value=quantity|selected）：
 * 以 productId 为身份的值对象，无审计时间字段。
 */
@Data
public class CartItem {
    private Long productId;
    private Integer quantity;
    private Boolean selected;
}
