package cc.ivera.cart.application.impl;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.cart.application.CartService;
import cc.ivera.cart.application.dto.CartItemRequest;
import cc.ivera.cart.application.dto.CartItemVO;
import cc.ivera.cart.domain.model.CartItem;
import cc.ivera.cart.domain.repository.CartRepository;
import cc.ivera.product.domain.model.Product;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductQueryApi productRepository;

    public CartServiceImpl(CartRepository cartRepository, ProductQueryApi productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Override
    public List<CartItemVO> list(Long userId) {
        List<CartItemVO> list = new ArrayList<>();
        for (CartItem item : cartRepository.findAll(userId)) {
            Product product = productRepository.findById(item.getProductId());
            CartItemVO vo = new CartItemVO();
            vo.setProductId(item.getProductId());
            vo.setQuantity(item.getQuantity());
            vo.setSelected(item.getSelected());
            if (product != null) {
                vo.setTitle(product.getTitle());
                vo.setLatestPrice(product.getPrice());
                vo.setAvailableStock(product.getStock());
                vo.setProductStatus(product.getProductStatus());
                vo.setAvailable(CommonStatus.ENABLED.getType().equals(product.getProductStatus())
                    && product.getStock() != null && product.getStock() > 0);
            } else {
                vo.setAvailable(false);
                vo.setProductStatus("MISSING");
            }
            list.add(vo);
        }
        list.sort(Comparator.comparing(CartItemVO::getProductId));
        return list;
    }

    @Override
    public void put(Long userId, CartItemRequest request) {
        Product product = productRepository.findById(request.getProductId());
        if (product == null) {
            throw new BizException("商品不存在");
        }
        if (!CommonStatus.ENABLED.getType().equals(product.getProductStatus())) {
            throw new BizException("商品已下架");
        }
        if (request.getQuantity() > product.getStock()) {
            throw new BizException("购物车数量不能超过当前可用库存");
        }
        CartItem item = new CartItem();
        item.setProductId(request.getProductId());
        item.setQuantity(request.getQuantity());
        item.setSelected(request.getSelected());
        cartRepository.save(userId, item);
    }

    @Override
    public void select(Long userId, Long productId, boolean selected) {
        CartItem item = cartRepository.findOne(userId, productId);
        if (item == null) {
            throw new BizException("购物车中不存在该商品");
        }
        item.setSelected(selected);
        cartRepository.save(userId, item);
    }

    @Override
    public void remove(Long userId, Long productId) {
        cartRepository.delete(userId, productId);
    }

    @Override
    public List<CartItemVO> selected(Long userId) {
        List<CartItemVO> list = list(userId);
        list.removeIf(vo -> !Boolean.TRUE.equals(vo.getSelected()));
        return list;
    }

    @Override
    public void clearSelected(Long userId) {
        for (CartItemVO vo : selected(userId)) {
            remove(userId, vo.getProductId());
        }
    }
}
