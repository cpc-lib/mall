package cc.ivera.cart.application.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class CartItemRequest {
    @NotNull
    private Long productId;
    @NotNull
    @Min(1)
    private Integer quantity;
    private Boolean selected;
}
