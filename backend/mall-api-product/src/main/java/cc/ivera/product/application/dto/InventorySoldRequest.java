package cc.ivera.product.application.dto;

import cc.ivera.product.domain.model.OrderItemStockLine;
import lombok.Data;

import java.util.List;

/**
 * 内部确认收货结转请求：订单服务传入明细快照，库存域不再反查订单。
 */
@Data
public class InventorySoldRequest {

    private String orderNo;

    private List<OrderItemStockLine> items;
}
