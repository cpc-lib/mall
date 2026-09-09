package cc.ivera.cart.application.impl;

import cc.ivera.cart.application.CartService;
import cc.ivera.cart.domain.model.CartItem;
import cc.ivera.cart.domain.repository.CartRepository;
import cc.ivera.cart.interfaces.dto.CartItemRequest;
import cc.ivera.cart.interfaces.vo.CartItemVO;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartServiceImpl(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Override
    public List<CartItemVO> list(Long userId) {
        List<CartItemVO> result = new ArrayList<>();
        for (CartItem e : cartRepository.findAll(userId)) {
            Product p = productRepository.findById(e.getProductId());
            CartItemVO vo = new CartItemVO();
            vo.setProductId(e.getProductId());
            vo.setQuantity(e.getQuantity());
            vo.setSelected(e.getSelected());
            if (p != null) {
                vo.setTitle(p.getTitle());
                vo.setLatestPrice(p.getPrice());
                vo.setAvailableStock(p.getStock());
                vo.setProductStatus(p.getProductStatus());
                vo.setAvailable(CommonStatus.ENABLED.getType().equals(p.getProductStatus()) && p.getStock() != null && p.getStock() > 0);
            } else {
                vo.setAvailable(false);
                vo.setProductStatus("MISSING");
            }
            result.add(vo);
        }
        result.sort(Comparator.comparing(CartItemVO::getProductId));
        return result;
    }

    @Override
    public void put(Long userId, CartItemRequest request) {
        Product product = productRepository.findById(request.getProductId());
        if (product == null) throw new BizException("商品不存在");
        if (!CommonStatus.ENABLED.getType().equals(product.getProductStatus())) throw new BizException("商品已下架");
        if (request.getQuantity() > product.getStock()) throw new BizException("购物车数量超过当前可用库存");
        CartItem item = new CartItem();
        item.setProductId(request.getProductId());
        item.setQuantity(request.getQuantity());
        item.setSelected(request.getSelected());
        cartRepository.save(userId, item);
    }

    @Override
    public void select(Long userId, Long productId, boolean selected) {
        CartItem item = cartRepository.findOne(userId, productId);
        if (item == null) throw new BizException("购物车中不存在该商品");
        item.setSelected(selected);
        cartRepository.save(userId, item);
    }

    @Override
    public void remove(Long userId, Long productId) {
        cartRepository.delete(userId, productId);
    }

    @Override
    public List<CartItemVO> selected(Long userId) {
        List<CartItemVO> all = list(userId);
        all.removeIf(v -> !Boolean.TRUE.equals(v.getSelected()));
        return all;
    }

    @Override
    public void clearSelected(Long userId) {
        for (CartItemVO item : selected(userId)) remove(userId, item.getProductId());
    }
}
