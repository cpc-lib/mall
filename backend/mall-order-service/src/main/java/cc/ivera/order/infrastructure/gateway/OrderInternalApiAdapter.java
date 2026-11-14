package cc.ivera.order.infrastructure.gateway;

import cc.ivera.api.order.OrderQueryApi;
import cc.ivera.api.order.OrderRefundQuotaApi;
import cc.ivera.api.order.OrderRefundStatusCommandApi;
import cc.ivera.api.order.OrderStatusCommandApi;
import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单上下文对外契约的进程内实现：只委托订单仓储/应用服务，不附加业务规则。
 * 订单服务拆分后，本类由远程客户端替换，契约不变。
 */
@Component
@RequiredArgsConstructor
public class OrderInternalApiAdapter implements OrderQueryApi, OrderRefundQuotaApi, OrderStatusCommandApi,
    OrderRefundStatusCommandApi {

    private final OrderRepository orderRepository;

    private final OrderInfoService orderInfoService;

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo);
    }

    @Override
    public List<OrderItem> listItemsByOrderNo(String orderNo) {
        return orderRepository.listItemsByOrderNo(orderNo);
    }

    @Override
    public OrderItem findItemById(Long itemId) {
        return orderRepository.findItemById(itemId);
    }

    @Override
    public int freezeOrderRefund(String orderNo, Integer amount) {
        return orderRepository.freezeOrderRefund(orderNo, amount);
    }

    @Override
    public int releaseOrderRefundFreeze(String orderNo, Integer amount) {
        return orderRepository.releaseOrderRefundFreeze(orderNo, amount);
    }

    @Override
    public int settleOrderRefund(String orderNo, Integer amount) {
        return orderRepository.settleOrderRefund(orderNo, amount);
    }

    @Override
    public int applyOrderRefundStatus(String orderNo) {
        return orderRepository.applyOrderRefundStatus(orderNo);
    }

    @Override
    public int freezeItemRefund(Long itemId, Integer qty, Integer amount) {
        return orderRepository.freezeItemRefund(itemId, qty, amount);
    }

    @Override
    public int releaseItemRefundFreeze(Long itemId, Integer qty, Integer amount) {
        return orderRepository.releaseItemRefundFreeze(itemId, qty, amount);
    }

    @Override
    public int settleItemRefund(Long itemId, Integer qty, Integer amount) {
        return orderRepository.settleItemRefund(itemId, qty, amount);
    }

    @Override
    public int addRestockedQty(Long itemId, Integer qty) {
        return orderRepository.addRestockedQty(itemId, qty);
    }

    @Override
    public int casCloseIfFullRefunded(String orderNo) {
        return orderRepository.casCloseIfFullRefunded(orderNo);
    }

    @Override
    public boolean markPaying(String orderNo) {
        return orderRepository.casUnpaidToPaying(orderNo);
    }

    @Override
    public boolean closeIfNotPay(String orderNo, OrderStatus target) {
        return orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, target);
    }

    @Override
    public void closeOversold(String orderNo) {
        orderRepository.updateLegacyStatus(orderNo, OrderStatus.OVER_SOLD_CLOSED.getType());
    }

    @Override
    public void updateLegacyStatus(String orderNo, OrderStatus status) {
        orderInfoService.updateStatusByOrderNo(orderNo, status);
    }
}
