package cc.ivera.cart.application.impl;

import cc.ivera.cart.domain.model.CartItem;
import cc.ivera.cart.domain.repository.CartRepository;
import cc.ivera.cart.interfaces.dto.CartItemRequest;
import cc.ivera.cart.interfaces.vo.CartItemVO;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CartServiceImpl 特征测试：mock CartRepository/ProductRepository 端口，锁定购物车现状业务规则，不连真实 Redis/DB。
 */
class CartServiceImplTest {

    private CartRepository cartRepository;
    private ProductRepository productRepository;
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        productRepository = mock(ProductRepository.class);
        cartService = new CartServiceImpl(cartRepository, productRepository);
    }

    private Product product(long id, String title, int price, int stock, String status) {
        Product p = new Product();
        p.setId(id);
        p.setTitle(title);
        p.setPrice(price);
        p.setStock(stock);
        p.setProductStatus(status);
        return p;
    }

    private CartItem item(long productId, int quantity, Boolean selected) {
        CartItem item = new CartItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setSelected(selected);
        return item;
    }

    private CartItemRequest putRequest(long productId, int quantity, Boolean selected) {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        request.setSelected(selected);
        return request;
    }

    @Test
    void putRejectsMissingProduct() {
        when(productRepository.findById(404L)).thenReturn(null);
        assertThrows(BizException.class, () -> cartService.put(1L, putRequest(404L, 1, true)));
        verify(cartRepository, never()).save(any(), any());
    }

    @Test
    void putRejectsDisabledProduct() {
        when(productRepository.findById(2L)).thenReturn(product(2L, "下架商品", 100, 10, CommonStatus.DISABLED.getType()));
        assertThrows(BizException.class, () -> cartService.put(1L, putRequest(2L, 1, true)));
        verify(cartRepository, never()).save(any(), any());
    }

    @Test
    void putRejectsQuantityOverStock() {
        when(productRepository.findById(3L)).thenReturn(product(3L, "紧俏商品", 100, 2, CommonStatus.ENABLED.getType()));
        assertThrows(BizException.class, () -> cartService.put(1L, putRequest(3L, 3, true)));
        verify(cartRepository, never()).save(any(), any());
    }

    @Test
    void putSavesItemAndPassesSelectedThrough() {
        when(productRepository.findById(1L)).thenReturn(product(1L, "手机", 199900, 5, CommonStatus.ENABLED.getType()));

        cartService.put(7L, putRequest(1L, 2, null));
        cartService.put(7L, putRequest(1L, 1, false));

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartRepository, times(2)).save(eq(7L), captor.capture());
        List<CartItem> saved = captor.getAllValues();
        assertEquals(Long.valueOf(1L), saved.get(0).getProductId());
        assertEquals(Integer.valueOf(2), saved.get(0).getQuantity());
        // selected=null 原样透传给仓储（由 Redis 编码归一为选中，现状）
        assertNull(saved.get(0).getSelected());
        assertEquals(Boolean.FALSE, saved.get(1).getSelected());
    }

    @Test
    void selectRejectsMissingItem() {
        when(cartRepository.findOne(1L, 99L)).thenReturn(null);
        assertThrows(BizException.class, () -> cartService.select(1L, 99L, true));
        verify(cartRepository, never()).save(any(), any());
    }

    @Test
    void selectUpdatesFlagKeepingQuantity() {
        when(cartRepository.findOne(1L, 5L)).thenReturn(item(5L, 3, true));

        cartService.select(1L, 5L, false);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartRepository).save(eq(1L), captor.capture());
        CartItem saved = captor.getValue();
        assertEquals(Long.valueOf(5L), saved.getProductId());
        assertEquals(Integer.valueOf(3), saved.getQuantity());
        assertEquals(Boolean.FALSE, saved.getSelected());
    }

    @Test
    void listAssemblesProductSnapshotAndSortsByProductId() {
        // 仓储返回无序：productId=2 在前
        when(cartRepository.findAll(1L)).thenReturn(Arrays.asList(item(2L, 1, true), item(1L, 2, false)));
        when(productRepository.findById(1L)).thenReturn(product(1L, "手机", 199900, 5, CommonStatus.ENABLED.getType()));
        when(productRepository.findById(2L)).thenReturn(null); // 现状：商品被删 → MISSING

        List<CartItemVO> result = cartService.list(1L);

        assertEquals(2, result.size());
        // 按 productId 升序
        assertEquals(Long.valueOf(1L), result.get(0).getProductId());
        CartItemVO first = result.get(0);
        assertEquals("手机", first.getTitle());
        assertEquals(Integer.valueOf(199900), first.getLatestPrice());
        assertEquals(Integer.valueOf(5), first.getAvailableStock());
        assertEquals(CommonStatus.ENABLED.getType(), first.getProductStatus());
        assertEquals(Integer.valueOf(2), first.getQuantity());
        assertEquals(Boolean.FALSE, first.getSelected());
        assertTrue(first.getAvailable());
        // 缺失商品：不可售 + MISSING 状态
        CartItemVO second = result.get(1);
        assertEquals(Long.valueOf(2L), second.getProductId());
        assertFalse(second.getAvailable());
        assertEquals("MISSING", second.getProductStatus());
        assertEquals(Integer.valueOf(1), second.getQuantity());
        assertEquals(Boolean.TRUE, second.getSelected());
    }

    @Test
    void listMarksUnavailableWhenDisabledOrOutOfStock() {
        when(cartRepository.findAll(1L)).thenReturn(Arrays.asList(
            item(1L, 1, true),
            item(2L, 1, true)));
        when(productRepository.findById(1L)).thenReturn(product(1L, "下架", 100, 5, CommonStatus.DISABLED.getType()));
        when(productRepository.findById(2L)).thenReturn(product(2L, "无货", 100, 0, CommonStatus.ENABLED.getType()));

        List<CartItemVO> result = cartService.list(1L);

        assertFalse(result.get(0).getAvailable());
        assertFalse(result.get(1).getAvailable());
    }

    @Test
    void selectedReturnsOnlyCheckedItems() {
        when(cartRepository.findAll(1L)).thenReturn(Arrays.asList(
            item(1L, 1, true),
            item(2L, 1, false),
            item(3L, 1, null)));
        when(productRepository.findById(1L)).thenReturn(product(1L, "A", 100, 5, CommonStatus.ENABLED.getType()));
        when(productRepository.findById(2L)).thenReturn(product(2L, "B", 100, 5, CommonStatus.ENABLED.getType()));
        when(productRepository.findById(3L)).thenReturn(product(3L, "C", 100, 5, CommonStatus.ENABLED.getType()));

        List<CartItemVO> result = cartService.selected(1L);

        assertEquals(1, result.size());
        assertEquals(Long.valueOf(1L), result.get(0).getProductId());
    }

    @Test
    void clearSelectedDeletesOnlyCheckedItems() {
        when(cartRepository.findAll(1L)).thenReturn(Arrays.asList(
            item(1L, 1, true),
            item(2L, 1, false)));
        when(productRepository.findById(1L)).thenReturn(product(1L, "A", 100, 5, CommonStatus.ENABLED.getType()));
        when(productRepository.findById(2L)).thenReturn(product(2L, "B", 100, 5, CommonStatus.ENABLED.getType()));

        cartService.clearSelected(1L);

        verify(cartRepository, times(1)).delete(1L, 1L);
        verify(cartRepository, never()).delete(eq(1L), eq(2L));
    }

    @Test
    void removeDelegatesToRepository() {
        cartService.remove(1L, 8L);
        verify(cartRepository).delete(1L, 8L);
    }

    @Test
    void listEmptyCartReturnsEmpty() {
        when(cartRepository.findAll(1L)).thenReturn(Collections.emptyList());
        assertTrue(cartService.list(1L).isEmpty());
    }
}
