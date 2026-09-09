package cc.ivera.cart.domain.repository;

import cc.ivera.cart.domain.model.CartItem;

import java.util.List;

/**
 * 购物车仓储：按用户存取购物车条目（Redis Hash：cart:&lt;userId&gt;，field=productId）。
 */
public interface CartRepository {

    /**
     * 用户购物车全部条目。
     */
    List<CartItem> findAll(Long userId);

    /**
     * 取单条条目；不存在返回 null。
     */
    CartItem findOne(Long userId, Long productId);

    /**
     * 写入/覆盖条目（同 productId 覆盖数量与选中态）。
     */
    void save(Long userId, CartItem item);

    /**
     * 删除条目；条目不存在时为空操作。
     */
    void delete(Long userId, Long productId);
}
