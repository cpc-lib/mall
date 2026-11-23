package cc.ivera.order.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.product.application.dto.InventoryReserveRequest;
import cc.ivera.product.application.dto.InventorySoldRequest;
import cc.ivera.product.domain.model.Product;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 商品服务内部契约：商品快照读取与库存预占推进。
 */
@FeignClient(name = "mall-product-service", url = "${product.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface ProductServiceClient {

    @GetMapping("/internal/product/query/{id}")
    R<Product> findById(@PathVariable("id") Long id);

    @PostMapping("/internal/product/query/batch")
    R<List<Product>> findByIds(@RequestBody List<Long> ids);

    @PostMapping("/internal/product/inventory/reserve")
    R<Object> reserve(@RequestBody InventoryReserveRequest request);

    @PostMapping("/internal/product/inventory/commit/{orderNo}")
    R<Object> commit(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/product/inventory/sold")
    R<Object> sold(@RequestBody InventorySoldRequest request);

    @PostMapping("/internal/product/inventory/release/{orderNo}")
    R<Object> release(@PathVariable("orderNo") String orderNo);
}
