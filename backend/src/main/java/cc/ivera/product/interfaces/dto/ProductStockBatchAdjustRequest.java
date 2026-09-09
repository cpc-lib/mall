package cc.ivera.product.interfaces.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量库存调整请求：逐商品明细列表，每个商品各自带调整量（正数补货、负数扣减）。
 */
@Data
public class ProductStockBatchAdjustRequest {

    @NotNull(message = "批量明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "库存调整量不能为空")
        private Integer delta; //正数补货、负数扣减，不能为 0（服务层校验）
    }
}
