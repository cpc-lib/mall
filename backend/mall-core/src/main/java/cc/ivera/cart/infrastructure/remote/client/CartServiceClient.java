package cc.ivera.cart.infrastructure.remote.client;

import cc.ivera.cart.application.dto.CartItemRequest;
import cc.ivera.cart.application.dto.CartItemVO;
import cc.ivera.cart.application.dto.CartSelectRequest;
import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车内部契约客户端：购物车由 mall-cart-service 持久化，路径对齐 CartInternalController。
 */
@FeignClient(name = "mall-cart-service", url = "${cart.service.base-url:}", configuration = InternalFeignConfiguration.class)
public interface CartServiceClient {

    @GetMapping("/internal/cart/{userId}")
    R<List<CartItemVO>> list(@PathVariable("userId") Long userId);

    @PostMapping("/internal/cart/{userId}/items")
    R<Object> put(@PathVariable("userId") Long userId, @RequestBody CartItemRequest request);

    @PostMapping("/internal/cart/{userId}/select")
    R<Object> select(@PathVariable("userId") Long userId, @RequestBody CartSelectRequest request);

    @DeleteMapping("/internal/cart/{userId}/items/{productId}")
    R<Object> remove(@PathVariable("userId") Long userId, @PathVariable("productId") Long productId);

    @GetMapping("/internal/cart/{userId}/selected")
    R<List<CartItemVO>> selected(@PathVariable("userId") Long userId);

    @DeleteMapping("/internal/cart/{userId}/selected")
    R<Object> clearSelected(@PathVariable("userId") Long userId);
}
