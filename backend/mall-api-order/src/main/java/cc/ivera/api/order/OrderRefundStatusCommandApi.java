package cc.ivera.api.order;

import cc.ivera.order.domain.enums.OrderStatus;

/**
 * 订单退款状态命令契约：退款上下文按退款单汇总结果回写订单展示状态。
 */
public interface OrderRefundStatusCommandApi {

    void updateLegacyStatus(String orderNo, OrderStatus status);
}
