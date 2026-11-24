package cc.ivera.cart.domain.repository;

import cc.ivera.cart.domain.model.CartItem;

import java.util.List;

/**
 * 购物车仓储端口：按 userId 隔离，userId + productId 唯一。
 */
public interface CartRepository {

    List<CartItem> findAll(Long userId);

    /**
     * 仅返回已勾选条目；selected 为 NULL 视为已勾选。
     */
    List<CartItem> findSelectedByUserId(Long userId);

    CartItem findOne(Long userId, Long productId);

    void save(Long userId, CartItem item);

    void delete(Long userId, Long productId);

    /**
     * 单条 DELETE 清空已勾选条目，避免逐条删除。
     */
    void deleteSelectedByUserId(Long userId);
}
