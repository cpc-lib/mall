package cc.ivera.vo;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderItem;
import lombok.Data;
import java.util.List;

@Data
public class OrderDetailVO {
    private OrderInfo order;
    private List<OrderItem> items;
    /** 物流单最新状态（SHIPPED/IN_TRANSIT/DELIVERED/RECEIVED/CANCELLED），无运单为 null；前端据此控制确认收货按钮（仅 DELIVERED 可确认）。 */
    private String shipmentStatus;
}
