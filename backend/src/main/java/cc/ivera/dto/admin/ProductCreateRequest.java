package cc.ivera.dto.admin;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
