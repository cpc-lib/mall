package cc.ivera.service;

/**
 * 异常支付自动冲正服务（V2）：由系统直接创建免审退款单并原路退款。
 * refund_type=DUPLICATE_PAYMENT / LATE_PAYMENT / OVER_SOLD：
 * 不冻结订单售后额度、不写 refund_item、不影响订单 refund_status，
 * 仅结转对应 PaymentOrder 的 refunded_amount（渠道资金防线内）。
 */
public interface ExceptionRefundService {

    /** 超卖熔断：支付成功后库存不足，整单自动原路退款并关闭订单。 */
    void trigger(String orderNo, String reason);

    /** 重复支付：订单已成交后同一订单又收到一笔不同支付单的成功支付。 */
    void duplicatePayment(String paymentNo);

    /** 晚到支付：订单已关闭/取消后渠道仍通知支付成功。 */
    void latePayment(String paymentNo);
}
