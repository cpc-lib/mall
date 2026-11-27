package cc.ivera.payment.interfaces;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付侧订单归属校验：普通用户只能操作本人订单，管理员免校验。
 */
class OrderAccessGuardTest {

    private final OrderPaymentApi orderPaymentApi = mock(OrderPaymentApi.class);
    private final OrderAccessGuard guard = new OrderAccessGuard(orderPaymentApi);

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    private void login(Long userId, String role) {
        AuthContext.set(new AuthPrincipal(userId, "u" + userId, role, 0L, "sid"));
    }

    private OrderInfo order(Long userId) {
        OrderInfo order = new OrderInfo();
        order.setOrderNo("ORD1");
        order.setUserId(userId);
        return order;
    }

    @Test
    @DisplayName("管理员直接放行，不查询订单")
    void adminSkipsOwnershipCheck() {
        login(9L, "ROLE_ADMIN");

        assertDoesNotThrow(() -> guard.assertOwnerOrAdmin("ORD1"));
        verify(orderPaymentApi, never()).getOrderByOrderNo("ORD1");
    }

    @Test
    @DisplayName("订单 owner 放行")
    void ownerPasses() {
        login(1L, "ROLE_USER");
        when(orderPaymentApi.getOrderByOrderNo("ORD1")).thenReturn(order(1L));

        assertDoesNotThrow(() -> guard.assertOwnerOrAdmin("ORD1"));
    }

    @Test
    @DisplayName("非 owner 拒绝")
    void otherUserRejected() {
        login(2L, "ROLE_USER");
        when(orderPaymentApi.getOrderByOrderNo("ORD1")).thenReturn(order(1L));

        assertThrows(BizException.class, () -> guard.assertOwnerOrAdmin("ORD1"));
    }

    @Test
    @DisplayName("订单不存在或 userId 为空（V1 遗留）一律拒绝")
    void missingOrderRejected() {
        login(1L, "ROLE_USER");
        when(orderPaymentApi.getOrderByOrderNo("ORD1")).thenReturn(null);
        assertThrows(BizException.class, () -> guard.assertOwnerOrAdmin("ORD1"));

        when(orderPaymentApi.getOrderByOrderNo("ORD2")).thenReturn(order(null));
        assertThrows(BizException.class, () -> guard.assertOwnerOrAdmin("ORD2"));
    }

    @Test
    @DisplayName("未登录拒绝")
    void anonymousRejected() {
        assertThrows(BizException.class, () -> guard.assertOwnerOrAdmin("ORD1"));
    }
}
