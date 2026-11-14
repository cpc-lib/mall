package cc.ivera.order.application.vo;

import cc.ivera.order.domain.model.OrderInfo;
import lombok.Data;

import java.util.List;

/**
 * 订单列表响应 VO。
 */
@Data
public class OrderListVO {

    private List<OrderInfo> list;
}
