package cc.ivera.product.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class ProductStatusRequest {
    @NotBlank
    private String productStatus; //ENABLED / DISABLED
}
