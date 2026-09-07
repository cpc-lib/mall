package cc.ivera.service.impl;

import cc.ivera.dto.cart.CartItemRequest;
import cc.ivera.entity.Product;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.service.CartService;
import cc.ivera.vo.CartItemVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CartServiceImpl implements CartService {
    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;
    public CartServiceImpl(StringRedisTemplate redisTemplate, ProductMapper productMapper) { this.redisTemplate = redisTemplate; this.productMapper = productMapper; }
    private String key(Long userId) { return "cart:" + userId; }

    @Override public List<CartItemVO> list(Long userId) {
        Map<Object,Object> raw = redisTemplate.opsForHash().entries(key(userId));
        List<CartItemVO> result = new ArrayList<>();
        for (Map.Entry<Object,Object> e : raw.entrySet()) {
            Long productId = Long.valueOf(String.valueOf(e.getKey())); String[] v = String.valueOf(e.getValue()).split("\\|");
            Product p = productMapper.selectById(productId);
            CartItemVO vo = new CartItemVO(); vo.setProductId(productId); vo.setQuantity(Integer.valueOf(v[0])); vo.setSelected(v.length < 2 || "1".equals(v[1]));
            if (p != null) { vo.setTitle(p.getTitle()); vo.setLatestPrice(p.getPrice()); vo.setAvailableStock(p.getStock()); vo.setProductStatus(p.getProductStatus()); vo.setAvailable("ENABLED".equals(p.getProductStatus()) && p.getStock() != null && p.getStock() > 0); }
            else { vo.setAvailable(false); vo.setProductStatus("MISSING"); }
            result.add(vo);
        }
        result.sort(Comparator.comparing(CartItemVO::getProductId)); return result;
    }
    @Override public void put(Long userId, CartItemRequest request) {
        Product product = productMapper.selectById(request.getProductId()); if (product == null) throw new BizException("商品不存在");
        if (!"ENABLED".equals(product.getProductStatus())) throw new BizException("商品已下架");
        if (request.getQuantity() > product.getStock()) throw new BizException("购物车数量超过当前可用库存");
        boolean selected = request.getSelected() == null || request.getSelected();
        redisTemplate.opsForHash().put(key(userId), String.valueOf(request.getProductId()), request.getQuantity() + "|" + (selected ? "1" : "0"));
    }
    @Override public void select(Long userId, Long productId, boolean selected) {
        Object old = redisTemplate.opsForHash().get(key(userId), String.valueOf(productId)); if (old == null) throw new BizException("购物车中不存在该商品");
        String[] v = String.valueOf(old).split("\\|"); redisTemplate.opsForHash().put(key(userId), String.valueOf(productId), v[0] + "|" + (selected ? "1" : "0"));
    }
    @Override public void remove(Long userId, Long productId) { redisTemplate.opsForHash().delete(key(userId), String.valueOf(productId)); }
    @Override public List<CartItemVO> selected(Long userId) { List<CartItemVO> all = list(userId); all.removeIf(v -> !Boolean.TRUE.equals(v.getSelected())); return all; }
    @Override public void clearSelected(Long userId) { for (CartItemVO item : selected(userId)) remove(userId, item.getProductId()); }
}
