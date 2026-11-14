package cc.ivera.order.infrastructure.remote;

import cc.ivera.cart.application.CartService;
import cc.ivera.cart.application.dto.CartItemRequest;
import cc.ivera.cart.application.dto.CartItemVO;
import cc.ivera.cart.application.dto.CartSelectRequest;
import cc.ivera.order.infrastructure.remote.client.CartServiceClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 购物车远程适配器：购物车数据由 mall-cart-service 持久化，订单只通过契约读取/清理。
 */
@Component
public class CartRemoteAdapter implements CartService {
    private static final String SERVICE = "购物车服务";

    private final CartServiceClient client;

    public CartRemoteAdapter(CartServiceClient client) {
        this.client = client;
    }

    @Override
    public List<CartItemVO> list(Long userId) {
        return RemoteResponseUnwrapper.unwrap(client.listCart(userId), SERVICE);
    }

    @Override
    public void put(Long userId, CartItemRequest request) {
        RemoteResponseUnwrapper.unwrap(client.putCartItem(userId, request), SERVICE);
    }

    @Override
    public void select(Long userId, Long productId, boolean selected) {
        CartSelectRequest request = new CartSelectRequest();
        request.setProductId(productId);
        request.setSelected(selected);
        RemoteResponseUnwrapper.unwrap(client.selectCartItem(userId, request), SERVICE);
    }

    @Override
    public void remove(Long userId, Long productId) {
        RemoteResponseUnwrapper.unwrap(client.removeCartItem(userId, productId), SERVICE);
    }

    @Override
    public List<CartItemVO> selected(Long userId) {
        return RemoteResponseUnwrapper.unwrap(client.listSelectedCart(userId), SERVICE);
    }

    @Override
    public void clearSelected(Long userId) {
        RemoteResponseUnwrapper.unwrap(client.clearSelectedCart(userId), SERVICE);
    }
}
