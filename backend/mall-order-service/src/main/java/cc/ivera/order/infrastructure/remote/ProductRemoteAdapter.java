package cc.ivera.order.infrastructure.remote;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.order.infrastructure.remote.client.ProductServiceClient;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.application.dto.InventoryReserveRequest;
import cc.ivera.product.application.dto.InventorySoldRequest;
import cc.ivera.product.domain.model.OrderItemStockLine;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ReserveLine;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 商品与库存远程适配器：商品/库存表只由商品服务写入，订单通过契约读取快照并推进预占。
 */
@Component
public class ProductRemoteAdapter implements ProductQueryApi, InventoryService {
    private static final String SERVICE = "商品服务";

    private final ProductServiceClient client;

    public ProductRemoteAdapter(ProductServiceClient client) {
        this.client = client;
    }

    @Override
    public Product findById(Long id) {
        return RemoteResponseUnwrapper.unwrap(client.findById(id), SERVICE);
    }

    @Override
    public void reserveForOrder(String orderNo, Date expireTime, List<ReserveLine> lines) {
        InventoryReserveRequest request = new InventoryReserveRequest();
        request.setOrderNo(orderNo);
        request.setExpireTime(expireTime);
        request.setLines(lines);
        RemoteResponseUnwrapper.unwrap(client.reserve(request), SERVICE);
    }

    @Override
    public void commitReservation(String orderNo) {
        RemoteResponseUnwrapper.unwrap(client.commit(orderNo), SERVICE);
    }

    @Override
    public void convertToSoldOnReceipt(String orderNo, List<OrderItemStockLine> items) {
        InventorySoldRequest request = new InventorySoldRequest();
        request.setOrderNo(orderNo);
        request.setItems(items);
        RemoteResponseUnwrapper.unwrap(client.sold(request), SERVICE);
    }

    @Override
    public void releaseReservation(String orderNo) {
        RemoteResponseUnwrapper.unwrap(client.release(orderNo), SERVICE);
    }

    @Override
    public void restockForRefund(String refundNo, List<cc.ivera.product.domain.model.RefundStockLine> items, boolean afterReceipt) {
        throw new UnsupportedOperationException("订单服务不执行退款回补");
    }

    @Override
    public void writeOffLostForRefund(String refundNo, List<cc.ivera.product.domain.model.RefundStockLine> items, boolean afterReceipt) {
        throw new UnsupportedOperationException("订单服务不执行退款核销");
    }
}
