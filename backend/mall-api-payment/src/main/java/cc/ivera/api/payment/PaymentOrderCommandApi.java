package cc.ivera.api.payment;

/**
 * 支付单命令契约：关单收口与渠道退款资金冻结/结转，均由支付上下文执行 CAS。
 */
public interface PaymentOrderCommandApi {

    /**
     * 关闭订单下全部活跃支付单（CREATED/PAYING → CLOSED）。
     */
    int closeActiveByOrderNo(String orderNo);

    /**
     * 渠道退款资金冻结：已退 + 冻结 + 本次 不超过实付金额。
     *
     * @return 1=冻结成功；0=渠道可退余额不足
     */
    int freezeChannelRefund(String paymentNo, Integer amount);

    /**
     * 渠道退款成功结转：冻结转已退。
     *
     * @return 1=结转成功；0=冻结不足
     */
    int settleChannelRefund(String paymentNo, Integer amount);
}
