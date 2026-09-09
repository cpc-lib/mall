package cc.ivera.cart.interfaces.vo;

import lombok.Data;

@Data
public class CartItemVO {
    private Long productId;
    private String title;
    private Integer latestPrice;
    private Integer availableStock;
    private String productStatus;
    private Integer quantity;
    private Boolean selected;
    private Boolean available;
}
