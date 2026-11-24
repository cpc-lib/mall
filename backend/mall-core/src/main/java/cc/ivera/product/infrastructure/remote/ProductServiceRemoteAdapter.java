package cc.ivera.product.infrastructure.remote;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.application.ProductService;
import cc.ivera.product.application.ProductStockService;
import cc.ivera.product.application.StockImportService;
import cc.ivera.product.application.dto.*;
import cc.ivera.product.application.vo.BatchAdjustResultVO;
import cc.ivera.product.domain.model.*;
import cc.ivera.product.infrastructure.remote.client.ProductServiceClient;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 商品服务远程适配器：BFF 与订单/退款/购物车经此调用独立商品服务，
 * 不保留本地商品表访问，也不做本地数据库 fallback。
 */
@Component
public class ProductServiceRemoteAdapter implements ProductService, ProductStockService, StockImportService,
    ProductQueryApi, InventoryService {
    private final ProductServiceClient client;

    public ProductServiceRemoteAdapter(ProductServiceClient client) {
        this.client = client;
    }

    @Override
    public Product findById(Long id) {
        return unwrap(client.query(id));
    }

    @Override
    public List<Product> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return unwrap(client.queryBatch(List.copyOf(ids)));
    }

    @Override
    public PageVO<Product> page(int page, int size) {
        return unwrap(client.page(page, size));
    }

    @Override
    public Product getById(Long id) {
        return unwrap(client.get(id));
    }

    @Override
    public Product create(String title, Integer price, Integer stock) {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setTitle(title);
        request.setPrice(price);
        request.setStock(stock);
        return unwrap(client.create(request));
    }

    @Override
    public Product changeStatus(Long id, String productStatus) {
        ProductStatusRequest request = new ProductStatusRequest();
        request.setProductStatus(productStatus);
        return unwrap(client.changeStatus(id, request));
    }

    @Override
    public Product adjustOne(Long productId, int delta) {
        ProductStockAdjustRequest request = new ProductStockAdjustRequest();
        request.setDelta(delta);
        return unwrap(client.adjust(productId, request));
    }

    @Override
    public void recordFailedAdjustment(Long productId, int delta, String reason) {
        StockAdjustFailedRequest request = new StockAdjustFailedRequest();
        request.setDelta(delta);
        request.setReason(reason);
        unwrap(client.recordFailedAdjustment(productId, request));
    }

    @Override
    public BatchAdjustResultVO batchAdjust(List<ProductStockBatchAdjustRequest.Item> items) {
        ProductStockBatchAdjustRequest request = new ProductStockBatchAdjustRequest();
        request.setItems(items);
        return unwrap(client.batchAdjust(request));
    }

    @Override
    public List<InventoryTransaction> recentTransactions(Long productId, int limit) {
        return unwrap(client.recentTransactions(productId, limit));
    }

    @Override
    public PageVO<InventoryTransaction> pageTransactions(int page, int size, Long productId, String bizType, String status) {
        return unwrap(client.pageTransactions(page, size, productId, bizType, status));
    }

    @Override
    public Long createImport(String fileName, String fileBase64, List<StockAdjustLine> items) {
        StockImportCreateRequest request = new StockImportCreateRequest();
        request.setFileName(fileName);
        request.setFileBase64(fileBase64);
        request.setItems(toAdjustItems(items));
        return unwrap(client.createImport(request));
    }

    @Override
    public PageVO<StockImport> listImports(int page, int size) {
        return unwrap(client.listImports(page, size));
    }

    @Override
    public StockImport getImport(Long id) {
        return unwrap(client.getImport(id));
    }

    @Override
    public String getFile(Long id) {
        return unwrap(client.getFile(id));
    }

    @Override
    public void saveImportFile(Long id, String fileBase64, List<StockAdjustLine> items) {
        StockImportFileSaveRequest request = new StockImportFileSaveRequest();
        request.setFileBase64(fileBase64);
        request.setItems(toAdjustItems(items));
        unwrap(client.saveImportFile(id, request));
    }

    @Override
    public StockAdjustResult confirmImport(Long id) {
        return unwrap(client.confirmImport(id));
    }

    @Override
    public void reserveForOrder(String orderNo, Date expireTime, List<ReserveLine> lines) {
        InventoryReserveRequest request = new InventoryReserveRequest();
        request.setOrderNo(orderNo);
        request.setExpireTime(expireTime);
        request.setLines(lines);
        unwrap(client.reserve(request));
    }

    @Override
    public void commitReservation(String orderNo) {
        unwrap(client.commitReservation(orderNo));
    }

    @Override
    public void releaseReservation(String orderNo) {
        unwrap(client.releaseReservation(orderNo));
    }

    @Override
    public void convertToSoldOnReceipt(String orderNo, List<OrderItemStockLine> items) {
        InventorySoldRequest request = new InventorySoldRequest();
        request.setOrderNo(orderNo);
        request.setItems(items);
        unwrap(client.convertToSold(request));
    }

    @Override
    public void restockForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt) {
        InventoryRefundRequest request = new InventoryRefundRequest();
        request.setRefundNo(refundNo);
        request.setItems(items);
        request.setAfterReceipt(afterReceipt);
        unwrap(client.restockForRefund(request));
    }

    @Override
    public void writeOffLostForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt) {
        InventoryRefundRequest request = new InventoryRefundRequest();
        request.setRefundNo(refundNo);
        request.setItems(items);
        request.setAfterReceipt(afterReceipt);
        unwrap(client.writeOffLostForRefund(request));
    }

    private List<ProductStockBatchAdjustRequest.Item> toAdjustItems(List<StockAdjustLine> items) {
        return items.stream().map(line -> {
            ProductStockBatchAdjustRequest.Item item = new ProductStockBatchAdjustRequest.Item();
            item.setProductId(line.getProductId());
            item.setDelta(line.getDelta());
            return item;
        }).toList();
    }

    private <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("商品服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "商品服务调用失败" : message);
    }
}
