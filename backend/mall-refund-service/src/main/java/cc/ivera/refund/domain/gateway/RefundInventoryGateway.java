package cc.ivera.refund.domain.gateway;

import cc.ivera.product.domain.model.RefundStockLine;

import java.util.List;

/**
 * 退款库存处置端口：退款上下文只表达回补/核销意图，库存规则与流水由商品服务执行。
 */
public interface RefundInventoryGateway {

    void restockForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt);

    void writeOffLostForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt);
}
