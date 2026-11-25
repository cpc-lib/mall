package cc.ivera.product.domain.model;

import lombok.Data;

/**
 * 下单预占行（订单上下文 → 库存上下文 的边界类型）：
 * 库存域不依赖订单实体，仅接收预占所需字段。
 * 预占先于落单执行时 orderItemId 为空，库存域以 (orderNo, productId) 作为幂等键。
 */
@Data
public class ReserveLine {

    private Long orderItemId;

    private Long productId;

    private Integer quantity;

    public ReserveLine() {
    }

    public ReserveLine(Long orderItemId, Long productId, Integer quantity) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.quantity = quantity;
    }
}
