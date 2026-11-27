package cc.ivera.payment.interfaces;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.security.AuthPrincipal;
import org.springframework.stereotype.Component;

/**
 * 支付侧订单操作前的归属校验：普通用户只能操作本人订单，管理员不受限。
 * <p>
 * 订单不存在、{@code userId} 为空（V1 免登录快捷下单遗留）或非本人时统一按
 * “订单不存在或无权访问”拒绝，避免通过错误信息探测订单是否存在。
 */
@Component
public class OrderAccessGuard {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final OrderPaymentApi orderPaymentApi;

    public OrderAccessGuard(OrderPaymentApi orderPaymentApi) {
        this.orderPaymentApi = orderPaymentApi;
    }

    public void assertOwnerOrAdmin(String orderNo) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) throw new BizException("未登录或登录已失效");
        if (ROLE_ADMIN.equals(principal.getRole())) return;
        OrderInfo order = orderPaymentApi.getOrderByOrderNo(orderNo);
        if (order == null || order.getUserId() == null || !order.getUserId().equals(principal.getUserId())) {
            throw new BizException("订单不存在或无权访问");
        }
    }
}
