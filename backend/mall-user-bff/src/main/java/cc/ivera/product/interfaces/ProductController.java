package cc.ivera.product.interfaces;

import cc.ivera.product.application.ProductService;
import cc.ivera.product.application.vo.SpuDetailVO;
import cc.ivera.product.application.vo.SpuSummaryVO;
import cc.ivera.product.domain.model.Product;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin //开放前端的跨域访问
@Tag(name = "商品管理")
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(
        ProductService productService
    ) {
        this.productService = productService;
    }

    @Operation(summary = "测试接口")
    @GetMapping("/test")
    public R<?> test() {
        return R.ok().setMessage("hello");
    }

    @Operation(summary = "商品列表（服务端分页）")
    @GetMapping("/page")
    public R<PageVO<Product>> page(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "12") int size) {
        return R.ok(productService.page(page, size));
    }

    @Operation(summary = "SPU 商品列表（仅展示上架商品，服务端分页）")
    @GetMapping("/spu/page")
    public R<PageVO<SpuSummaryVO>> spuPage(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "12") int size,
                                           @RequestParam(required = false) String keyword) {
        return R.ok(productService.pageSpu(page, size, keyword, true));
    }

    @Operation(summary = "SPU 商品详情（含基础展示属性、销售规格树与 SKU 列表）")
    @GetMapping("/spu/{spuId}")
    public R<SpuDetailVO> spuDetail(@PathVariable Long spuId) {
        return R.ok(productService.getSpuDetail(spuId));
    }

}
