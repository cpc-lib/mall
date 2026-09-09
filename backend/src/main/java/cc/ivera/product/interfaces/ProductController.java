package cc.ivera.product.interfaces;

import cc.ivera.product.application.ProductService;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.interfaces.vo.ProductListVO;
import cc.ivera.shared.web.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin //开放前端的跨域访问
@Api(tags = "商品管理")
@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(
        ProductService productService
    ) {
        this.productService = productService;
    }

    @ApiOperation("测试接口")
    @GetMapping("/test")
    public R<?> test() {
        return R.ok().setMessage("hello");
    }

    @ApiOperation("商品列表")
    @GetMapping("/list")
    public R<ProductListVO> list() {
        List<Product> list = productService.list();
        ProductListVO vo = new ProductListVO();
        vo.setProductList(list);
        return R.ok(vo);
    }

}
