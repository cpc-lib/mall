package cc.ivera.product.interfaces;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.application.ProductService;
import cc.ivera.product.application.ProductStockService;
import cc.ivera.product.application.StockImportService;
import cc.ivera.product.application.dto.*;
import cc.ivera.product.domain.model.InventoryTransaction;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.StockAdjustLine;
import cc.ivera.product.domain.model.StockImport;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品与库存服务内部接口：仅服务间调用，路由不经过网关，由内部 Token 认证。
 */
@RestController
@RequestMapping("/internal/product")
public class ProductInternalController {

    private final ProductService productService;
    private final ProductQueryApi productQueryApi;
    private final ProductStockService productStockService;
    private final StockImportService stockImportService;
    private final InventoryService inventoryService;

    public ProductInternalController(ProductService productService, ProductQueryApi productQueryApi,
                                     ProductStockService productStockService, StockImportService stockImportService,
                                     InventoryService inventoryService) {
        this.productService = productService;
        this.productQueryApi = productQueryApi;
        this.productStockService = productStockService;
        this.stockImportService = stockImportService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/products")
    public R<List<Product>> list() {
        return R.ok(productService.list());
    }

    @GetMapping("/products/page")
    public R<PageVO<Product>> page(@RequestParam int page, @RequestParam int size) {
        return R.ok(productService.page(page, size));
    }

    @GetMapping("/products/{id}")
    public R<Product> get(@PathVariable Long id) {
        return R.ok(productService.getById(id));
    }

    @PostMapping("/products")
    public R<Product> create(@Valid @RequestBody ProductCreateRequest request) {
        return R.ok(productService.create(request.getTitle(), request.getPrice(), request.getStock()));
    }

    @PutMapping("/products/{id}/status")
    public R<Product> changeStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return R.ok(productService.changeStatus(id, request.getProductStatus()));
    }

    @GetMapping("/query/{id}")
    public R<Product> query(@PathVariable Long id) {
        return R.ok(productQueryApi.findById(id));
    }

    @PostMapping("/query/batch")
    public R<List<Product>> queryBatch(@RequestBody List<Long> ids) {
        return R.ok(productQueryApi.findByIds(ids));
    }

    @PostMapping("/stock/{id}/adjust")
    public R<Product> adjust(@PathVariable Long id, @Valid @RequestBody ProductStockAdjustRequest request) {
        return R.ok(productStockService.adjustOne(id, request.getDelta()));
    }

    @PostMapping("/stock/{id}/failed-adjust")
    public R<?> recordFailedAdjustment(@PathVariable Long id, @Valid @RequestBody StockAdjustFailedRequest request) {
        productStockService.recordFailedAdjustment(id, request.getDelta(), request.getReason());
        return R.ok();
    }

    @GetMapping("/stock/{id}/transactions")
    public R<List<InventoryTransaction>> recentTransactions(@PathVariable Long id, @RequestParam int limit) {
        return R.ok(productStockService.recentTransactions(id, limit));
    }

    @GetMapping("/stock/transactions")
    public R<PageVO<InventoryTransaction>> pageTransactions(@RequestParam int page, @RequestParam int size,
                                                             @RequestParam(required = false) Long productId,
                                                             @RequestParam(required = false) String bizType,
                                                             @RequestParam(required = false) String status) {
        return R.ok(productStockService.pageTransactions(page, size, productId, bizType, status));
    }

    @PostMapping("/imports")
    public R<Long> createImport(@Valid @RequestBody StockImportCreateRequest request) {
        return R.ok(stockImportService.createImport(request.getFileName(), request.getFileBase64(),
            toStockAdjustLines(request.getItems())));
    }

    @GetMapping("/imports")
    public R<List<StockImport>> listImports() {
        return R.ok(stockImportService.listImports());
    }

    @GetMapping("/imports/{id}")
    public R<StockImport> getImport(@PathVariable Long id) {
        return R.ok(stockImportService.getImport(id));
    }

    @GetMapping("/imports/{id}/file")
    public R<String> getFile(@PathVariable Long id) {
        return R.ok(stockImportService.getFile(id));
    }

    @PutMapping("/imports/{id}/file")
    public R<?> saveFile(@PathVariable Long id, @Valid @RequestBody StockImportFileSaveRequest request) {
        stockImportService.saveImportFile(id, request.getFileBase64(), toStockAdjustLines(request.getItems()));
        return R.ok();
    }

    @PostMapping("/imports/{id}/confirm")
    public R<StockAdjustResult> confirmImport(@PathVariable Long id) {
        return R.ok(stockImportService.confirmImport(id));
    }

    @PostMapping("/inventory/reserve")
    public R<?> reserve(@RequestBody InventoryReserveRequest request) {
        inventoryService.reserveForOrder(request.getOrderNo(), request.getExpireTime(), request.getLines());
        return R.ok();
    }

    @PostMapping("/inventory/commit/{orderNo}")
    public R<?> commit(@PathVariable String orderNo) {
        inventoryService.commitReservation(orderNo);
        return R.ok();
    }

    @PostMapping("/inventory/release/{orderNo}")
    public R<?> release(@PathVariable String orderNo) {
        inventoryService.releaseReservation(orderNo);
        return R.ok();
    }

    @PostMapping("/inventory/sold")
    public R<?> sold(@RequestBody InventorySoldRequest request) {
        inventoryService.convertToSoldOnReceipt(request.getOrderNo(), request.getItems());
        return R.ok();
    }

    @PostMapping("/inventory/refund-restock")
    public R<?> refundRestock(@RequestBody InventoryRefundRequest request) {
        inventoryService.restockForRefund(request.getRefundNo(), request.getItems(), request.isAfterReceipt());
        return R.ok();
    }

    @PostMapping("/inventory/refund-lost")
    public R<?> refundLost(@RequestBody InventoryRefundRequest request) {
        inventoryService.writeOffLostForRefund(request.getRefundNo(), request.getItems(), request.isAfterReceipt());
        return R.ok();
    }

    private List<StockAdjustLine> toStockAdjustLines(List<ProductStockBatchAdjustRequest.Item> items) {
        if (items == null) return List.of();
        return items.stream()
            .map(i -> new StockAdjustLine(i.getProductId(), i.getDelta()))
            .collect(java.util.stream.Collectors.toList());
    }
}
