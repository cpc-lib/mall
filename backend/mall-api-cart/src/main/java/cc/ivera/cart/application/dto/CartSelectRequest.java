package cc.ivera.cart.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class CartSelectRequest {
    @NotNull
    private Long productId;
    @NotNull
    private Boolean selected;
}
