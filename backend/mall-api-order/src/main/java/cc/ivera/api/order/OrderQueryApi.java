package cc.ivera.api.order;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;

import java.util.Collection;
import java.util.List;

/**
 * 订单只读契约：供支付、退款、账单等上下文读取订单快照。
 */
public interface OrderQueryApi {

    OrderInfo findByOrderNo(String orderNo);

    /**
     * 批量读取订单快照：列表页一次取回，避免按单号逐次远程查询。
     */
    List<OrderInfo> findByOrderNos(Collection<String> orderNos);

    List<OrderItem> listItemsByOrderNo(String orderNo);

    OrderItem findItemById(Long itemId);
}
