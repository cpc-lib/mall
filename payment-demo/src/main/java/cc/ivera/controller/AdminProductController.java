package cc.ivera.controller;

import cc.ivera.dto.admin.ProductCreateRequest;
import cc.ivera.dto.admin.ProductStatusRequest;
import cc.ivera.dto.admin.ProductStockAdjustRequest;
import cc.ivera.dto.admin.ProductStockBatchAdjustRequest;
import cc.ivera.entity.Product;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.service.ProductStockService;
import cc.ivera.vo.BatchAdjustItemVO;
import cc.ivera.vo.BatchAdjustResultVO;
import cc.ivera.vo.ProductDetailVO;
import cc.ivera.vo.R;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.*;

/**
 * 管理员商品管理：新增商品 / 商品详情 / 库存调整（单条+批量）/ 上下架。
 * 库存调整与商品详情日志统一走 V2 库存流水表 t_inventory_transaction
 * （旧 t_stock_operation_log 自 V2 起仅为历史审计，交易链路不再写入）。
 */
@RestController @RequestMapping("/api/admin/products") @CrossOrigin
@Validated
@Api(tags = "管理员商品管理API")
public class AdminProductController {
    private final ProductMapper productMapper;
    private final ProductStockService productStockService;
    public AdminProductController(ProductMapper productMapper, ProductStockService productStockService){
        this.productMapper=productMapper; this.productStockService=productStockService;
    }

    @ApiOperation("商品列表")
    @GetMapping
    public R<List<Product>> list(){return R.ok(productMapper.selectList(new QueryWrapper<Product>().orderByAsc("id")));}

    @ApiOperation("商品详情（含最近20条库存流水）")
    @GetMapping("/{id}")
    public R<ProductDetailVO> detail(@PathVariable Long id){
        Product product = productMapper.selectById(id);
        if (product == null) throw new BizException("商品不存在");
        ProductDetailVO result = new ProductDetailVO();
        result.setProduct(product);
        result.setLogs(productStockService.recentLogs(id, 20));
        return R.ok(result);
    }

    @ApiOperation("新增商品")
    @PostMapping
    public R<Product> create(@Valid @RequestBody ProductCreateRequest req){
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) throw new BizException("商品名称不能为空");
        if (req.getPrice() == null || req.getPrice() <= 0) throw new BizException("价格必须大于 0");
        if (req.getStock() == null || req.getStock() < 0) throw new BizException("库存不能为负数");
        Product p = new Product();
        p.setTitle(req.getTitle().trim());
        p.setPrice(req.getPrice());
        p.setStock(req.getStock());
        p.setLockedStock(0);
        p.setProductStatus("ENABLED");
        p.setCreateTime(new Date());
        p.setUpdateTime(new Date());
        productMapper.insert(p);
        return R.ok(p).setMessage("商品已创建");
    }

    @ApiOperation("调整库存（落 MANUAL_ADJUST 流水，失败也记录 FAILED 流水）")
    @PostMapping("/{id}/stock")
    public R<Product> adjustStock(@PathVariable Long id, @Valid @RequestBody ProductStockAdjustRequest req){
        int delta = req.getDelta();
        try {
            Product product = productStockService.adjustOne(id, delta);
            return R.ok(product).setMessage(delta > 0 ? "补货成功" : "库存已扣减");
        } catch (BizException e) {
            productStockService.recordFailedAdjustment(id, delta, e.getMessage());
            throw e;
        }
    }

    @ApiOperation("批量调整库存（逐条处理：成功的生效落流水，失败的返回原因并记录 FAILED 流水）")
    @PostMapping("/stock/batch")
    public R<BatchAdjustResultVO> batchAdjustStock(@Valid @RequestBody ProductStockBatchAdjustRequest req){
        List<ProductStockBatchAdjustRequest.Item> items = req.getItems();
        if (items == null || items.isEmpty()) throw new BizException("批量明细不能为空");
        List<BatchAdjustItemVO> results = new ArrayList<>();
        int successCount = 0;
        for (ProductStockBatchAdjustRequest.Item item : items) {
            BatchAdjustItemVO row = new BatchAdjustItemVO();
            row.setProductId(item.getProductId());
            row.setDelta(item.getDelta());
            try {
                Product product = productStockService.adjustOne(item.getProductId(), item.getDelta());
                row.setSuccess(true);
                row.setStock(product.getStock());
                row.setMessage(item.getDelta() > 0 ? "补货成功" : "库存已扣减");
                successCount++;
            } catch (BizException e) {
                productStockService.recordFailedAdjustment(item.getProductId(), item.getDelta(), e.getMessage());
                row.setSuccess(false);
                row.setMessage(e.getMessage());
            }
            results.add(row);
        }
        BatchAdjustResultVO body = new BatchAdjustResultVO();
        body.setTotal(items.size());
        body.setSuccessCount(successCount);
        body.setFailCount(items.size() - successCount);
        body.setResults(results);
        return R.ok(body).setMessage("批量调整完成：成功 " + successCount + " 条，失败 " + (items.size() - successCount) + " 条");
    }

    @ApiOperation("上架/下架")
    @PostMapping("/{id}/status")
    public R<Product> setStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest req){
        if (!Arrays.asList("ENABLED", "DISABLED").contains(req.getProductStatus())) throw new BizException("商品状态仅支持 ENABLED / DISABLED");
        Product product = productMapper.selectById(id);
        if (product == null) throw new BizException("商品不存在");
        product.setProductStatus(req.getProductStatus());
        product.setUpdateTime(new Date());
        productMapper.updateById(product);
        return R.ok(product).setMessage("ENABLED".equals(req.getProductStatus()) ? "商品已上架" : "商品已下架");
    }
}
