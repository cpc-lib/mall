package cc.ivera.product.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ProductStockAdjustRequest {
    @NotNull
    private Integer delta; //库存调整量，正数补货、负数扣减
}
