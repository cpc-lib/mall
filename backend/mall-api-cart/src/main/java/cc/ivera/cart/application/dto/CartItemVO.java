package cc.ivera.cart.application.dto;

import lombok.Data;

@Data
public class CartItemVO {
    private Long productId;
    private Long spuId;
    private String title;
    private String specText;
    private Integer latestPrice;
    private Integer availableStock;
    private String productStatus;
    private Integer quantity;
    private Boolean selected;
    private Boolean available;
}
