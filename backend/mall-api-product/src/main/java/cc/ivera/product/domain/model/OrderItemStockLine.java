package cc.ivera.product.domain.model;

import lombok.Data;

/**
 * 订单成交行库存视图（订单上下文 → 库存上下文 的边界类型）：
 * 确认收货结转已售时，库存域只需明细 id、商品、购买数量与已回补数量，不依赖订单实体。
 */
@Data
public class OrderItemStockLine {

    private Long orderItemId;

    private Long productId;

    private Integer quantity;

    private Integer restockedQty;

    public OrderItemStockLine() {
    }

    public OrderItemStockLine(Long orderItemId, Long productId, Integer quantity, Integer restockedQty) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.quantity = quantity;
        this.restockedQty = restockedQty;
    }
}
