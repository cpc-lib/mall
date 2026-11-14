package cc.ivera.refund.infrastructure.remote;

import cc.ivera.product.application.dto.InventoryRefundRequest;
import cc.ivera.product.domain.model.RefundStockLine;
import cc.ivera.refund.domain.gateway.RefundInventoryGateway;
import cc.ivera.refund.infrastructure.remote.client.ProductServiceFeignClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退款库存处置远程适配器：调用商品服务既有库存内部接口，退款侧不持有库存规则。
 */
@Component
public class RefundInventoryRemoteAdapter extends AbstractRefundRemoteAdapter implements RefundInventoryGateway {

    private final ProductServiceFeignClient client;

    public RefundInventoryRemoteAdapter(ProductServiceFeignClient client) {
        this.client = client;
    }

    @Override
    public void restockForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt) {
        unwrap(client.refundRestock(request(refundNo, items, afterReceipt)));
    }

    @Override
    public void writeOffLostForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt) {
        unwrap(client.refundLost(request(refundNo, items, afterReceipt)));
    }

    private InventoryRefundRequest request(String refundNo, List<RefundStockLine> items, boolean afterReceipt) {
        InventoryRefundRequest request = new InventoryRefundRequest();
        request.setRefundNo(refundNo);
        request.setItems(items);
        request.setAfterReceipt(afterReceipt);
        return request;
    }
}
