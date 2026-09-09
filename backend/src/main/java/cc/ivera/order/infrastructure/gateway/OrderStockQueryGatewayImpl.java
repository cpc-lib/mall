package cc.ivera.order.infrastructure.gateway;

import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.product.domain.gateway.OrderStockQueryGateway;
import cc.ivera.product.domain.model.OrderItemStockLine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单上下文对库存域的 ACL 实现：把订单明细领域模型翻译为库存域边界类型，
 * 库存动作需要的明细读取/数量累计经此防腐层，不触碰订单基础设施。
 */
@Component
public class OrderStockQueryGatewayImpl implements OrderStockQueryGateway {

    private final OrderRepository orderRepository;

    public OrderStockQueryGatewayImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public List<OrderItemStockLine> listItemsByOrderNo(String orderNo) {
        return orderRepository.listItemsByOrderNo(orderNo).stream()
            .map(this::toStockLine)
            .collect(Collectors.toList());
    }

    @Override
    public boolean addRestockedQty(Long orderItemId, Integer qty) {
        return orderRepository.addRestockedQty(orderItemId, qty) > 0;
    }

    private OrderItemStockLine toStockLine(OrderItem item) {
        return new OrderItemStockLine(item.getId(), item.getProductId(), item.getQuantity(), item.getRestockedQty());
    }
}
