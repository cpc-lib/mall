package cc.ivera.cart.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.product.domain.model.Product;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务内部契约客户端：购物车只读取商品快照，不访问商品库。
 */
@FeignClient(name = "mall-product-service", url = "${product.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface ProductQueryFeignClient {

    @GetMapping("/internal/product/query/{id}")
    R<Product> findById(@PathVariable("id") Long id);
}
