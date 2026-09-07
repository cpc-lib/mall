package cc.ivera.vo;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderItem;
import lombok.Data;
import java.util.List;

@Data
public class OrderDetailVO {
    private OrderInfo order;
    private List<OrderItem> items;
}
