package cc.ivera.service;

import cc.ivera.dto.cart.CartItemRequest;
import cc.ivera.vo.CartItemVO;

import java.util.List;

public interface CartService {
    List<CartItemVO> list(Long userId);
    void put(Long userId, CartItemRequest request);
    void select(Long userId, Long productId, boolean selected);
    void remove(Long userId, Long productId);
    List<CartItemVO> selected(Long userId);
    void clearSelected(Long userId);
}
