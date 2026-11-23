package cc.ivera.cart.application.impl;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.cart.application.dto.CartItemVO;
import cc.ivera.cart.domain.model.CartItem;
import cc.ivera.cart.domain.repository.CartRepository;
import cc.ivera.product.domain.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CartServiceImplTest {

    @Test
    void loadsProductSnapshotInSingleBatchCall() {
        CartRepository cartRepository = mock(CartRepository.class);
        ProductQueryApi productQueryApi = mock(ProductQueryApi.class);
        when(cartRepository.findAll(9L)).thenReturn(List.of(item(2L, 1), item(1L, 3)));
        Product product = Product.create("商品A", 1999, 8);
        product.setId(1L);
        when(productQueryApi.findByIds(any())).thenReturn(List.of(product));

        List<CartItemVO> result = new CartServiceImpl(cartRepository, productQueryApi).list(9L);

        verify(productQueryApi, times(1)).findByIds(eq(List.of(2L, 1L)));
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getProductId());
        assertEquals("商品A", result.get(0).getTitle());
        assertEquals(1999, result.get(0).getLatestPrice());
        assertEquals(8, result.get(0).getAvailableStock());
        assertTrue(result.get(0).getAvailable());
        assertEquals(2L, result.get(1).getProductId());
        assertEquals("MISSING", result.get(1).getProductStatus());
        assertFalse(result.get(1).getAvailable());
    }

    @Test
    void skipsProductQueryWhenCartEmpty() {
        CartRepository cartRepository = mock(CartRepository.class);
        ProductQueryApi productQueryApi = mock(ProductQueryApi.class);
        when(cartRepository.findAll(9L)).thenReturn(List.of());

        List<CartItemVO> result = new CartServiceImpl(cartRepository, productQueryApi).list(9L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(productQueryApi);
    }

    private static CartItem item(Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setSelected(true);
        return item;
    }
}
