package cc.ivera.refund.infrastructure.remote;

import cc.ivera.api.order.OrderQueryApi;
import cc.ivera.api.order.OrderRefundQuotaApi;
import cc.ivera.api.order.OrderRefundStatusCommandApi;
import cc.ivera.api.order.OrderStatusCommandApi;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.refund.infrastructure.remote.client.OrderServiceFeignClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 订单退款远程适配器：退款上下文只读订单快照、申请退款额度并回写订单展示状态。
 */
@Component
public class OrderRefundRemoteAdapter extends AbstractRefundRemoteAdapter
    implements OrderQueryApi, OrderRefundQuotaApi, OrderStatusCommandApi, OrderRefundStatusCommandApi {

    private final OrderServiceFeignClient client;

    public OrderRefundRemoteAdapter(OrderServiceFeignClient client) {
        this.client = client;
    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        return unwrap(client.findByOrderNo(orderNo));
    }

    @Override
    public List<OrderItem> listItemsByOrderNo(String orderNo) {
        return unwrap(client.listItemsByOrderNo(orderNo));
    }

    @Override
    public OrderItem findItemById(Long itemId) {
        return unwrap(client.findItemById(itemId));
    }

    @Override
    public int freezeOrderRefund(String orderNo, Integer amount) {
        return orZero(unwrap(client.freezeOrderRefund(orderNo, Map.of("amount", amount))));
    }

    @Override
    public int releaseOrderRefundFreeze(String orderNo, Integer amount) {
        return orZero(unwrap(client.releaseOrderRefundFreeze(orderNo, Map.of("amount", amount))));
    }

    @Override
    public int settleOrderRefund(String orderNo, Integer amount) {
        return orZero(unwrap(client.settleOrderRefund(orderNo, Map.of("amount", amount))));
    }

    @Override
    public int applyOrderRefundStatus(String orderNo) {
        return orZero(unwrap(client.applyOrderRefundStatus(orderNo)));
    }

    @Override
    public int freezeItemRefund(Long itemId, Integer qty, Integer amount) {
        return orZero(unwrap(client.freezeItemRefund(itemId, quotaBody(qty, amount))));
    }

    @Override
    public int releaseItemRefundFreeze(Long itemId, Integer qty, Integer amount) {
        return orZero(unwrap(client.releaseItemRefundFreeze(itemId, quotaBody(qty, amount))));
    }

    @Override
    public int settleItemRefund(Long itemId, Integer qty, Integer amount) {
        return orZero(unwrap(client.settleItemRefund(itemId, quotaBody(qty, amount))));
    }

    @Override
    public int addRestockedQty(Long itemId, Integer qty) {
        return orZero(unwrap(client.addRestockedQty(itemId, Map.of("qty", qty))));
    }

    @Override
    public int casCloseIfFullRefunded(String orderNo) {
        return orZero(unwrap(client.casCloseIfFullRefunded(orderNo)));
    }

    @Override
    public boolean markPaying(String orderNo) {
        return Boolean.TRUE.equals(unwrap(client.markPaying(Map.of("orderNo", orderNo))));
    }

    @Override
    public boolean closeIfNotPay(String orderNo, OrderStatus target) {
        return Boolean.TRUE.equals(unwrap(client.closeIfNotPay(
            Map.of("orderNo", orderNo, "target", target.name()))));
    }

    @Override
    public void closeOversold(String orderNo) {
        unwrap(client.closeOversold(Map.of("orderNo", orderNo)));
    }

    @Override
    public void updateLegacyStatus(String orderNo, OrderStatus status) {
        unwrap(client.updateRefundStatus(orderNo, Map.of("status", status.name())));
    }

    private Map<String, Object> quotaBody(Integer qty, Integer amount) {
        return Map.of("qty", qty, "amount", amount);
    }

    private static int orZero(Integer result) {
        return result == null ? 0 : result;
    }
}
