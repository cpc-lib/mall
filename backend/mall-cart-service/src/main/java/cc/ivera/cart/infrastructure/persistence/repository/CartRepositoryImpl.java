package cc.ivera.cart.infrastructure.persistence.repository;

import cc.ivera.cart.domain.model.CartItem;
import cc.ivera.cart.domain.repository.CartRepository;
import cc.ivera.cart.infrastructure.persistence.converter.CartItemPOConverter;
import cc.ivera.cart.infrastructure.persistence.mapper.CartItemMapper;
import cc.ivera.cart.infrastructure.persistence.po.CartItemPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class CartRepositoryImpl implements CartRepository {

    private final CartItemMapper cartItemMapper;

    public CartRepositoryImpl(CartItemMapper cartItemMapper) {
        this.cartItemMapper = cartItemMapper;
    }

    @Override
    public List<CartItem> findAll(Long userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItemPO>()
                .eq(CartItemPO::getUserId, userId)
                .orderByAsc(CartItemPO::getProductId))
            .stream().map(CartItemPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<CartItem> findSelectedByUserId(Long userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItemPO>()
                .eq(CartItemPO::getUserId, userId)
                .and(w -> w.eq(CartItemPO::getSelected, "1").or().isNull(CartItemPO::getSelected))
                .orderByAsc(CartItemPO::getProductId))
            .stream().map(CartItemPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public CartItem findOne(Long userId, Long productId) {
        return CartItemPOConverter.toDomain(cartItemMapper.selectOne(new LambdaQueryWrapper<CartItemPO>()
            .eq(CartItemPO::getUserId, userId)
            .eq(CartItemPO::getProductId, productId)
            .last("limit 1")));
    }

    @Override
    public void save(Long userId, CartItem item) {
        CartItemPO po = CartItemPOConverter.toPO(userId, item);
        cartItemMapper.upsert(userId, po.getProductId(), po.getQuantity(), po.getSelected());
    }

    @Override
    public void delete(Long userId, Long productId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItemPO>()
            .eq(CartItemPO::getUserId, userId)
            .eq(CartItemPO::getProductId, productId));
    }

    @Override
    public void deleteSelectedByUserId(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItemPO>()
            .eq(CartItemPO::getUserId, userId)
            .and(w -> w.eq(CartItemPO::getSelected, "1").or().isNull(CartItemPO::getSelected)));
    }
}
