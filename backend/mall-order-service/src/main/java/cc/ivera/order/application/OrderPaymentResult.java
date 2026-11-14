package cc.ivera.order.application;

/**
 * 支付成交推进订单的结果。
 */
public enum OrderPaymentResult {

    /** 本次成功推进为已支付。 */
    PAID,

    /** 订单已是已支付，幂等。 */
    ALREADY_PAID,

    /** 订单已关闭/取消，不能再推进为已支付（晚到支付）。 */
    CLOSED,

    /** 订单不存在。 */
    NOT_FOUND
}
