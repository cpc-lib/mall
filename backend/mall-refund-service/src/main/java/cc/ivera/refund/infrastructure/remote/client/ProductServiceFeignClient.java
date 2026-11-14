package cc.ivera.refund.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.product.application.dto.InventoryRefundRequest;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 商品服务内部契约客户端：路径与 {@code ProductInternalController} 保持一一对应。
 */
@FeignClient(name = "mall-product-service", url = "${product.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface ProductServiceFeignClient {

    @PostMapping("/internal/product/inventory/refund-restock")
    R<Object> refundRestock(@RequestBody InventoryRefundRequest request);

    @PostMapping("/internal/product/inventory/refund-lost")
    R<Object> refundLost(@RequestBody InventoryRefundRequest request);
}
