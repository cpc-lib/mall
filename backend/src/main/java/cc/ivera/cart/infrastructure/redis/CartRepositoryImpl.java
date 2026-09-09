package cc.ivera.cart.infrastructure.redis;

import cc.ivera.cart.domain.model.CartItem;
import cc.ivera.cart.domain.repository.CartRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 购物车 Redis Hash 仓储：key=cart:&lt;userId&gt;，field=productId，value=quantity|selected(0/1)。
 * 历史数据无 selected 段时按选中（true）处理（现状）。
 */
@Repository
public class CartRepositoryImpl implements CartRepository {

    private final StringRedisTemplate redisTemplate;

    public CartRepositoryImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String key(Long userId) {
        return "cart:" + userId;
    }

    @Override
    public List<CartItem> findAll(Long userId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key(userId));
        List<CartItem> result = new ArrayList<>();
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            result.add(decode(String.valueOf(e.getKey()), String.valueOf(e.getValue())));
        }
        return result;
    }

    @Override
    public CartItem findOne(Long userId, Long productId) {
        Object raw = redisTemplate.opsForHash().get(key(userId), String.valueOf(productId));
        return raw == null ? null : decode(String.valueOf(productId), String.valueOf(raw));
    }

    @Override
    public void save(Long userId, CartItem item) {
        redisTemplate.opsForHash().put(key(userId), String.valueOf(item.getProductId()), encode(item));
    }

    @Override
    public void delete(Long userId, Long productId) {
        redisTemplate.opsForHash().delete(key(userId), String.valueOf(productId));
    }

    private String encode(CartItem item) {
        boolean selected = item.getSelected() == null || item.getSelected();
        return item.getQuantity() + "|" + (selected ? "1" : "0");
    }

    private CartItem decode(String field, String value) {
        String[] v = value.split("\\|");
        CartItem item = new CartItem();
        item.setProductId(Long.valueOf(field));
        item.setQuantity(Integer.valueOf(v[0]));
        item.setSelected(v.length < 2 || "1".equals(v[1]));
        return item;
    }
}
