package cc.ivera.product.application.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class ProductCreateRequest {
    @NotBlank(message = "商品名称不能为空")
    private String title;
    @NotNull(message = "价格不能为空")
    @Min(value = 1, message = "价格必须大于 0")
    private Integer price; // 分
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;
}
