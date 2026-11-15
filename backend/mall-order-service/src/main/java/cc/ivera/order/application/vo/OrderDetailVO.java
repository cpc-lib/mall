package cc.ivera.order.application.vo;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import lombok.Data;

import java.util.List;

@Data
public class OrderDetailVO {
    private OrderInfo order;
    private List<OrderItem> items;
}
