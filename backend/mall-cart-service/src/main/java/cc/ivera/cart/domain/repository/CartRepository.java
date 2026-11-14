package cc.ivera.cart.domain.repository;

import cc.ivera.cart.domain.model.CartItem;

import java.util.List;

/**
 * 购物车仓储端口：按 userId 隔离，userId + productId 唯一。
 */
public interface CartRepository {

    List<CartItem> findAll(Long userId);

    CartItem findOne(Long userId, Long productId);

    void save(Long userId, CartItem item);

    void delete(Long userId, Long productId);
}
