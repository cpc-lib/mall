package cc.ivera.cart.interfaces;

import cc.ivera.cart.application.CartService;
import cc.ivera.cart.interfaces.dto.CartItemRequest;
import cc.ivera.cart.interfaces.dto.CartSelectRequest;
import cc.ivera.cart.interfaces.vo.CartItemVO;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public R<List<CartItemVO>> list() {
        return R.ok(cartService.list(AuthContext.userId()));
    }

    @PostMapping("/item")
    public R<?> put(@Valid @RequestBody CartItemRequest req) {
        cartService.put(AuthContext.userId(), req);
        return R.ok().setMessage("购物车已更新");
    }

    @PostMapping("/select")
    public R<?> select(@Valid @RequestBody CartSelectRequest req) {
        cartService.select(AuthContext.userId(), req.getProductId(), req.getSelected());
        return R.ok();
    }

    @DeleteMapping("/item/{productId}")
    public R<?> remove(@PathVariable Long productId) {
        cartService.remove(AuthContext.userId(), productId);
        return R.ok();
    }
}
