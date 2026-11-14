package cc.ivera.product.application.dto;

import cc.ivera.product.domain.model.RefundStockLine;
import lombok.Data;

import java.util.List;

/**
 * 内部退款库存处置请求：退款上下文传入已受理的明细数量，库存域不反查订单。
 */
@Data
public class InventoryRefundRequest {

    private String refundNo;

    private List<RefundStockLine> items;

    private boolean afterReceipt;
}
