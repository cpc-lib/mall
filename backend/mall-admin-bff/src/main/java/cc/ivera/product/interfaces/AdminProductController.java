package cc.ivera.product.interfaces;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.product.application.ProductService;
import cc.ivera.product.application.ProductStockService;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.interfaces.assembler.ProductViewAssembler;
import cc.ivera.product.application.dto.ProductCreateRequest;
import cc.ivera.product.application.dto.ProductStatusRequest;
import cc.ivera.product.application.dto.ProductStockAdjustRequest;
import cc.ivera.product.application.dto.ProductStockBatchAdjustRequest;
import cc.ivera.product.application.vo.BatchAdjustResultVO;
import cc.ivera.product.application.vo.ProductDetailVO;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 管理员商品管理：新增商品 / 商品详情 / 库存调整（单条+批量）/ 上下架。
 * 库存调整与商品详情日志统一走 V2 库存流水表 t_inventory_transaction。
 */
@RestController
@RequestMapping("/api/admin/products")
@CrossOrigin
@Validated
@Tag(name = "管理员商品管理API")
public class AdminProductController {
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final ProductQueryApi productQueryApi;

    public AdminProductController(ProductService productService, ProductStockService productStockService,
                                  ProductQueryApi productQueryApi) {
        this.productService = productService;
        this.productStockService = productStockService;
        this.productQueryApi = productQueryApi;
    }

    @Operation(summary = "商品列表（服务端分页）")
    @GetMapping("/page")
    public R<PageVO<Product>> page(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(productService.page(page, size));
    }

    @Operation(summary = "按 id 批量查询商品（供库存导入结果补全商品名称）")
    @PostMapping("/batch")
    public R<List<Product>> batch(@RequestBody List<Long> ids) {
        return R.ok(productQueryApi.findByIds(ids));
    }

    @Operation(summary = "商品详情（含最近20条库存流水）")
    @GetMapping("/{id}")
    public R<ProductDetailVO> detail(@PathVariable Long id) {
        Product product = productService.getById(id);
        ProductDetailVO result = new ProductDetailVO();
        result.setProduct(product);
        result.setLogs(ProductViewAssembler.toTransactionVOList(productStockService.recentTransactions(id, 20)));
        return R.ok(result);
    }

    @Operation(summary = "新增商品")
    @PostMapping
    public R<Product> create(@Valid @RequestBody ProductCreateRequest req) {
        Product p = productService.create(req.getTitle(), req.getPrice(), req.getStock());
        return R.ok(p).setMessage("商品已创建");
    }

    @Operation(summary = "调整库存（落 MANUAL_ADJUST 流水，失败也记录 FAILED 流水）")
    @PostMapping("/{id}/stock")
    public R<Product> adjustStock(@PathVariable Long id, @Valid @RequestBody ProductStockAdjustRequest req) {
        int delta = req.getDelta();
        try {
            Product product = productStockService.adjustOne(id, delta);
            return R.ok(product).setMessage(delta > 0 ? "补货成功" : "库存已扣减");
        } catch (BizException e) {
            productStockService.recordFailedAdjustment(id, delta, e.getMessage());
            throw e;
        }
    }

    @Operation(summary = "批量调整库存（逐条处理：成功的生效落流水，失败的返回原因并记录 FAILED 流水）")
    @PostMapping("/stock/batch")
    public R<BatchAdjustResultVO> batchAdjustStock(@Valid @RequestBody ProductStockBatchAdjustRequest req) {
        List<ProductStockBatchAdjustRequest.Item> items = req.getItems();
        if (items == null || items.isEmpty()) throw new BizException("批量明细不能为空");
        // 循环下沉到 mall-product-service，单次 Feign 完成全部明细（每条独立事务）
        BatchAdjustResultVO body = productStockService.batchAdjust(items);
        return R.ok(body).setMessage("批量调整完成：成功 " + body.getSuccessCount() + " 条，失败 " + body.getFailCount() + " 条");
    }

    @Operation(summary = "上架/下架")
    @PostMapping("/{id}/status")
    public R<Product> setStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest req) {
        Product product = productService.changeStatus(id, req.getProductStatus());
        return R.ok(product).setMessage(CommonStatus.ENABLED.getType().equals(req.getProductStatus()) ? "商品已上架" : "商品已下架");
    }
}
