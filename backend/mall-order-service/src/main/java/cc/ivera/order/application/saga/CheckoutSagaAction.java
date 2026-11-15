package cc.ivera.order.application.saga;

import cc.ivera.cart.application.CartService;
import cc.ivera.order.application.dto.CheckoutRequest;
import cc.ivera.order.application.impl.CheckoutTxService;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.domain.model.ReserveLine;
import cc.ivera.shared.domain.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 下单 Saga 各步骤的动作实现：每个方法都是本地事务内可重放的幂等操作，
 * 由 Seata 状态机按 order-checkout.json 的顺序与补偿关系驱动。
 */
@Component("checkoutSagaAction")
public class CheckoutSagaAction {

    private final CheckoutTxService checkoutTxService;
    private final InventoryService inventoryService;
    private final CartService cartService;

    public CheckoutSagaAction(CheckoutTxService checkoutTxService,
                              InventoryService inventoryService,
                              CartService cartService) {
        this.checkoutTxService = checkoutTxService;
        this.inventoryService = inventoryService;
        this.cartService = cartService;
    }

    /**
     * 步骤一：落单（本地事务）。同一 orderNo 已存在时返回既有上下文，保证重放幂等。
     */
    public Map<String, Object> createOrder(Map<String, Object> checkout) {
        Long userId = asLong(checkout.get("userId"));
        String orderNo = asString(checkout.get("orderNo"));
        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentType(asString(checkout.get("paymentType")));
        request.setPaymentAppId(asLong(checkout.get("paymentAppId")));
        request.setAddressId(asLong(checkout.get("addressId")));
        request.setReceiverName(asString(checkout.get("receiverName")));
        request.setReceiverPhone(asString(checkout.get("receiverPhone")));
        request.setReceiverAddress(asString(checkout.get("receiverAddress")));
        try {
            return checkoutTxService.prepare(userId, request, orderNo);
        } catch (RuntimeException e) {
            throw wrap("落单", e);
        }
    }

    /**
     * createOrder 的补偿：仅未支付订单会被关闭，重复执行安全。
     */
    public void cancelOrder(Map<String, Object> context) {
        if (context == null) return;
        try {
            checkoutTxService.cancelIfUnpaid(asString(context.get("orderNo")));
        } catch (RuntimeException e) {
            throw wrap("订单取消补偿", e);
        }
    }

    /**
     * 步骤二：预占库存。reserveForOrder 自身以 (order_item_id) 唯一键作幂等闸门，可重放。
     */
    public void reserveInventory(Map<String, Object> context) {
        try {
            List<ReserveLine> lines = new ArrayList<>();
            Object items = context.get("items");
            if (items instanceof List<?>) {
                for (Object item : (List<?>) items) {
                    if (!(item instanceof Map<?, ?>)) {
                        continue;
                    }
                    Map<?, ?> line = (Map<?, ?>) item;
                    lines.add(new ReserveLine(asLong(line.get("orderItemId")), asLong(line.get("productId")),
                        asInt(line.get("quantity"))));
                }
            }
            inventoryService.reserveForOrder(asString(context.get("orderNo")),
                new Date(asLong(context.get("expireTime"))), lines);
        } catch (RuntimeException e) {
            throw wrap("库存预占", e);
        }
    }

    /**
     * reserveInventory 的补偿：释放预占，库存域内幂等。
     */
    public void releaseInventory(Map<String, Object> context) {
        if (context == null) return;
        try {
            inventoryService.releaseReservation(asString(context.get("orderNo")));
        } catch (RuntimeException e) {
            throw wrap("库存释放补偿", e);
        }
    }

    /**
     * 步骤三：清空购物车已勾选商品。
     */
    public void clearCart(Map<String, Object> context) {
        try {
            cartService.clearSelected(asLong(context.get("userId")));
        } catch (RuntimeException e) {
            throw wrap("清空购物车", e);
        }
    }

    /**
     * 业务异常原样上抛；其它异常（典型为 Feign 远程异常）收敛为可序列化的 BizException。
     * Seata 需要把异常写入状态日志，Feign 异常持有不可序列化的 Target，直接透出会导致状态日志落库失败。
     */
    private static RuntimeException wrap(String action, RuntimeException e) {
        if (e instanceof BizException) {
            return e;
        }
        String message = e.getMessage();
        return new BizException(action + "失败：" + (message == null || message.isBlank()
            ? e.getClass().getSimpleName() : message));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private static int asInt(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Saga 上下文缺少数量");
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }
}
