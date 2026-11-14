package cc.ivera.cart.interfaces;

import cc.ivera.cart.application.CartService;
import cc.ivera.cart.application.dto.CartItemRequest;
import cc.ivera.cart.application.dto.CartItemVO;
import cc.ivera.cart.application.dto.CartSelectRequest;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 购物车服务间契约：由 BFF 与订单服务通过 /internal/cart/** 调用，不经过网关。
 */
@RestController
@RequestMapping("/internal/cart")
public class CartInternalController {
    private final CartService cartService;

    public CartInternalController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{userId}")
    public R<List<CartItemVO>> list(@PathVariable Long userId) {
        return R.ok(cartService.list(userId));
    }

    @PostMapping("/{userId}/items")
    public R<?> put(@PathVariable Long userId, @Valid @RequestBody CartItemRequest request) {
        cartService.put(userId, request);
        return R.ok();
    }

    @PostMapping("/{userId}/select")
    public R<?> select(@PathVariable Long userId, @Valid @RequestBody CartSelectRequest request) {
        cartService.select(userId, request.getProductId(), request.getSelected());
        return R.ok();
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public R<?> remove(@PathVariable Long userId, @PathVariable Long productId) {
        cartService.remove(userId, productId);
        return R.ok();
    }

    @GetMapping("/{userId}/selected")
    public R<List<CartItemVO>> selected(@PathVariable Long userId) {
        return R.ok(cartService.selected(userId));
    }

    @DeleteMapping("/{userId}/selected")
    public R<?> clearSelected(@PathVariable Long userId) {
        cartService.clearSelected(userId);
        return R.ok();
    }
}
