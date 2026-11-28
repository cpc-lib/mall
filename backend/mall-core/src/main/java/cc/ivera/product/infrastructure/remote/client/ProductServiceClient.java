package cc.ivera.product.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.product.application.dto.*;
import cc.ivera.product.application.vo.BatchAdjustResultVO;
import cc.ivera.product.application.vo.SpuDetailVO;
import cc.ivera.product.application.vo.SpuSummaryVO;
import cc.ivera.product.domain.model.InventoryTransaction;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.StockImport;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品与库存内部契约客户端：路径与参数对齐 mall-product-service 的 ProductInternalController。
 */
@FeignClient(name = "mall-product-service", url = "${product.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface ProductServiceClient {

    @GetMapping("/internal/product/query/{id}")
    R<Product> query(@PathVariable("id") Long id);

    @PostMapping("/internal/product/query/batch")
    R<List<Product>> queryBatch(@RequestBody List<Long> ids);

    @GetMapping("/internal/product/products/page")
    R<PageVO<Product>> page(@RequestParam("page") int page, @RequestParam("size") int size);

    @GetMapping("/internal/product/products/{id}")
    R<Product> get(@PathVariable("id") Long id);

    @PostMapping("/internal/product/products")
    R<Product> create(@RequestBody ProductCreateRequest request);

    @PutMapping("/internal/product/products/{id}/status")
    R<Product> changeStatus(@PathVariable("id") Long id, @RequestBody ProductStatusRequest request);

    @GetMapping("/internal/product/spus/page")
    R<PageVO<SpuSummaryVO>> pageSpu(@RequestParam("page") int page,
                                    @RequestParam("size") int size,
                                    @RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "onlyEnabled", defaultValue = "false") boolean onlyEnabled);

    @GetMapping("/internal/product/spus/{spuId}")
    R<SpuDetailVO> getSpuDetail(@PathVariable("spuId") Long spuId);

    @PostMapping("/internal/product/spus")
    R<SpuDetailVO> createSpu(@RequestBody SpuSaveRequest request);

    @PutMapping("/internal/product/spus/{spuId}")
    R<SpuDetailVO> updateSpu(@PathVariable("spuId") Long spuId, @RequestBody SpuSaveRequest request);

    @PutMapping("/internal/product/spus/{spuId}/status")
    R<SpuDetailVO> changeSpuStatus(@PathVariable("spuId") Long spuId, @RequestBody ProductStatusRequest request);

    @PostMapping("/internal/product/stock/{id}/adjust")
    R<Product> adjust(@PathVariable("id") Long id, @RequestBody ProductStockAdjustRequest request);

    @PostMapping("/internal/product/stock/{id}/failed-adjust")
    R<Object> recordFailedAdjustment(@PathVariable("id") Long id, @RequestBody StockAdjustFailedRequest request);

    @PostMapping("/internal/product/stock/batch-adjust")
    R<BatchAdjustResultVO> batchAdjust(@RequestBody ProductStockBatchAdjustRequest request);

    @GetMapping("/internal/product/stock/{id}/transactions")
    R<List<InventoryTransaction>> recentTransactions(@PathVariable("id") Long id, @RequestParam("limit") int limit);

    @GetMapping("/internal/product/stock/transactions")
    R<PageVO<InventoryTransaction>> pageTransactions(@RequestParam("page") int page,
                                                     @RequestParam("size") int size,
                                                     @RequestParam(value = "productId", required = false) Long productId,
                                                     @RequestParam(value = "bizType", required = false) String bizType,
                                                     @RequestParam(value = "status", required = false) String status);

    @PostMapping("/internal/product/imports")
    R<Long> createImport(@RequestBody StockImportCreateRequest request);

    @GetMapping("/internal/product/imports")
    R<PageVO<StockImport>> listImports(@RequestParam("page") int page, @RequestParam("size") int size);

    @GetMapping("/internal/product/imports/{id}")
    R<StockImport> getImport(@PathVariable("id") Long id);

    @GetMapping("/internal/product/imports/{id}/file")
    R<String> getFile(@PathVariable("id") Long id);

    @PutMapping("/internal/product/imports/{id}/file")
    R<Object> saveImportFile(@PathVariable("id") Long id, @RequestBody StockImportFileSaveRequest request);

    @PostMapping("/internal/product/imports/{id}/confirm")
    R<StockAdjustResult> confirmImport(@PathVariable("id") Long id);

    @PostMapping("/internal/product/inventory/reserve")
    R<Object> reserve(@RequestBody InventoryReserveRequest request);

    @PostMapping("/internal/product/inventory/commit/{orderNo}")
    R<Object> commitReservation(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/product/inventory/release/{orderNo}")
    R<Object> releaseReservation(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/product/inventory/sold")
    R<Object> convertToSold(@RequestBody InventorySoldRequest request);

    @PostMapping("/internal/product/inventory/refund-restock")
    R<Object> restockForRefund(@RequestBody InventoryRefundRequest request);

    @PostMapping("/internal/product/inventory/refund-lost")
    R<Object> writeOffLostForRefund(@RequestBody InventoryRefundRequest request);
}
