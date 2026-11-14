package cc.ivera.api.order;

import cc.ivera.order.domain.enums.OrderStatus;

/**
 * 订单状态命令契约：只暴露跨上下文需要的语义化状态推进，
 * 不暴露通用状态写入口，避免外部绕过订单状态机。
 */
public interface OrderStatusCommandApi {

    /**
     * 渠道下单成功：订单支付状态 UNPAID → PAYING（CAS，幂等）。
     */
    boolean markPaying(String orderNo);

    /**
     * 仅当订单仍为未支付时推进到目标终态（CLOSED / CANCEL），
     * 由订单侧统一完成关支付单、释放库存预占与关单消息收口。
     */
    boolean closeIfNotPay(String orderNo, OrderStatus target);

    /**
     * 超卖关单：订单置为超卖关闭终态（V1 兼容入口，无 CAS 条件）。
     */
    void closeOversold(String orderNo);
}
