package cc.ivera.order.application.dto;

import cc.ivera.order.domain.enums.OrderStatus;
import lombok.Data;

/**
 * 内部契约请求：仅当订单仍未支付时推进到目标终态。
 */
@Data
public class OrderCloseIfNotPayRequest {

    private String orderNo;

    private OrderStatus target;
}
