package cc.ivera.cart.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class CartSelectRequest {
    @NotNull
    private Long productId;
    @NotNull
    private Boolean selected;
}
