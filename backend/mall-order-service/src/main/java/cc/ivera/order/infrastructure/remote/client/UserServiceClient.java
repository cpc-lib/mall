package cc.ivera.order.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.dto.AccessTokenRequest;
import cc.ivera.user.domain.model.ShippingAddress;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务内部契约：收货地址与 Access Token 校验。购物车已迁移至 mall-cart-service。
 */
@FeignClient(name = "mall-user-service", url = "${user.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface UserServiceClient {

    @GetMapping("/internal/user/addresses")
    R<List<ShippingAddress>> listAddresses(@RequestParam("userId") Long userId);

    @GetMapping("/internal/user/addresses/{id}")
    R<ShippingAddress> getAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId);

    @PostMapping("/internal/user/addresses")
    R<ShippingAddress> createAddress(@RequestParam("userId") Long userId, @RequestBody ShippingAddress address);

    @PutMapping("/internal/user/addresses/{id}")
    R<ShippingAddress> updateAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId,
                                     @RequestBody ShippingAddress address);

    @DeleteMapping("/internal/user/addresses/{id}")
    R<Object> deleteAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId);

    @PutMapping("/internal/user/addresses/{id}/default")
    R<Object> setDefaultAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId);

    @GetMapping("/internal/user/addresses/default-of/current")
    R<ShippingAddress> defaultAddress(@RequestParam("userId") Long userId);

    @PostMapping("/internal/user/auth/verify-access-token")
    R<AuthPrincipal> verifyAccessToken(@RequestBody AccessTokenRequest request);
}
