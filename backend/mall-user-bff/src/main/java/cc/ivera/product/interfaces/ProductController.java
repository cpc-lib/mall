package cc.ivera.product.interfaces;

import cc.ivera.product.application.ProductService;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.application.vo.ProductListVO;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @Operation(summary = "商品列表")
    @GetMapping("/list")
    public R<ProductListVO> list() {
        List<Product> list = productService.list();
        ProductListVO vo = new ProductListVO();
        vo.setProductList(list);
        return R.ok(vo);
    }

    @Operation(summary = "商品列表（服务端分页）")
    @GetMapping("/page")
    public R<PageVO<Product>> page(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "12") int size) {
        return R.ok(productService.page(page, size));
    }

}
