package cc.ivera.product.domain.model;

import lombok.Data;

/**
 * 退款库存处理行（退款上下文 → 库存上下文 的边界类型）：
 * 库存域不依赖退款实体，仅接收回补/核销所需字段。
 */
@Data
public class RefundStockLine {

    private Long orderItemId;

    private Long productId;

    private Integer refundQty;

    public RefundStockLine() {
    }

    public RefundStockLine(Long orderItemId, Long productId, Integer refundQty) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.refundQty = refundQty;
    }
}
