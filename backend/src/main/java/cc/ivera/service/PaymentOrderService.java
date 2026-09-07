package cc.ivera.service;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.PaymentOrder;

/**
 * 支付单生命周期服务（V2）：本地订单 1:N 渠道支付尝试。
 */
public interface PaymentOrderService {

    /**
     * 发起支付：为订单确保一笔活跃支付单（CREATED）。
     * 幂等：同订单存在活跃支付单（CREATED/PAYING）时直接复用；
     * 订单临近过期（&lt;60s）拒绝发起；expire_time = min(order.expire_time, now+2h)。
     */
    PaymentOrder startPayment(OrderInfo order, String channel);

    /**
     * 渠道下单成功：CREATED → PAYING（CAS），记录二维码。
     */
    void markPaying(String paymentNo, String codeUrl);

    /**
     * 支付成功：CREATED/PAYING → SUCCESS（CAS），记录渠道交易号与实付金额。
     *
     * @return true=CAS 成功；false=已被并发处理（幂等）
     */
    boolean markSuccess(String paymentNo, String channelOrderNo, Integer paidAmount);
}
