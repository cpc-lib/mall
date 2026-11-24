package cc.ivera.cart.infrastructure.remote;

import cc.ivera.cart.application.CartService;
import cc.ivera.cart.application.dto.CartItemRequest;
import cc.ivera.cart.application.dto.CartItemVO;
import cc.ivera.cart.application.dto.CartSelectRequest;
import cc.ivera.cart.infrastructure.remote.client.CartServiceClient;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 购物车远程适配器：购物车数据由购物车服务持久化，BFF 与订单只读取/写入契约。
 */
@Component
public class CartServiceRemoteAdapter implements CartService {
    private final CartServiceClient client;

    public CartServiceRemoteAdapter(CartServiceClient client) {
        this.client = client;
    }

    @Override
    public List<CartItemVO> list(Long userId) {
        return unwrap(client.list(userId));
    }

    @Override
    public void put(Long userId, CartItemRequest request) {
        unwrap(client.put(userId, request));
    }

    @Override
    public void select(Long userId, Long productId, boolean selected) {
        CartSelectRequest request = new CartSelectRequest();
        request.setProductId(productId);
        request.setSelected(selected);
        unwrap(client.select(userId, request));
    }

    @Override
    public void remove(Long userId, Long productId) {
        unwrap(client.remove(userId, productId));
    }

    @Override
    public List<CartItemVO> selected(Long userId) {
        return unwrap(client.selected(userId));
    }

    @Override
    public void clearSelected(Long userId) {
        unwrap(client.clearSelected(userId));
    }

    private <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("购物车服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "购物车服务调用失败" : message);
    }
}
