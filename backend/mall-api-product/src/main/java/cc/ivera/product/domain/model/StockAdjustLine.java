package cc.ivera.product.domain.model;

import lombok.Data;

/**
 * 库存调整行（导入明细/批量调整的领域类型）：productId + delta（正补货、负扣减）。
 * JSON 字段名与导入明细快照一致（productId/delta），items_json 读写兼容。
 */
@Data
public class StockAdjustLine {

    private Long productId;

    private Integer delta;

    public StockAdjustLine() {
    }

    public StockAdjustLine(Long productId, Integer delta) {
        this.productId = productId;
        this.delta = delta;
    }
}
