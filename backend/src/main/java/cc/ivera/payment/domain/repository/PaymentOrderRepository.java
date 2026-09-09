package cc.ivera.payment.domain.repository;

import cc.ivera.payment.domain.model.PaymentOrder;

import java.util.List;

/**
 * 支付单仓储端口（聚合根 PaymentOrder）：状态流转 CAS 闸门落基础设施 SQL，
 * 端口只表达领域语义（行锁查询/活跃单复用/成交收口/状态 CAS）。
 */
public interface PaymentOrderRepository {

    /**
     * 新建支付单。
     */
    void save(PaymentOrder paymentOrder);

    /**
     * 按支付单编号查询并加行级排他锁（支付成功处理/关单互斥）。
     */
    PaymentOrder findByPaymentNoForUpdate(String paymentNo);

    /**
     * 同订单同渠道最新一笔活跃支付单（CREATED/PAYING），渠道感知复用。
     */
    PaymentOrder findActiveByOrderNoAndChannel(String orderNo, String channel);

    /**
     * 同订单同渠道最新一笔成交支付单（SUCCESS）。
     */
    PaymentOrder findLatestSuccessByOrderNoAndChannel(String orderNo, String channel);

    /**
     * 订单全部支付单（管理端支付尝试记录，按 id 升序）。
     */
    List<PaymentOrder> listByOrderNoAsc(String orderNo);

    /**
     * 关闭订单下全部活跃支付单（CREATED/PAYING → CLOSED）。
     */
    int closeActiveByOrderNo(String orderNo);

    /**
     * 订单成交收口：关闭除成交支付单外的其它渠道活跃支付单。
     */
    int closeActiveByOrderNoExceptPaymentNo(String orderNo, String paymentNo);

    /**
     * 渠道下单成功：CREATED → PAYING（CAS），记录二维码。
     *
     * @return true=CAS 成功；false=状态已非 CREATED（幂等忽略）
     */
    boolean markPaying(String paymentNo, String codeUrl);

    /**
     * 支付成功：CREATED/PAYING → SUCCESS（CAS），记录渠道交易号与实付金额。
     *
     * @return true=CAS 成功；false=已被并发处理（幂等）
     */
    boolean markSuccess(String paymentNo, String channelOrderNo, Integer paidAmount);

    /**
     * 渠道退款资金冻结：累计已退+冻结+本次 不超过实付金额。
     *
     * @return 1=冻结成功；0=渠道可退余额不足
     */
    int freezeChannelRefund(String paymentNo, Integer amount);

    /**
     * 渠道退款成功结转：冻结转已退。
     *
     * @return 1=结转成功；0=冻结不足（未冻结或已结转）
     */
    int settleChannelRefund(String paymentNo, Integer amount);

    /**
     * 退款发起前查找成交支付单：优先返回最新一笔 SUCCESS（按支付时间倒序）；
     * 若无 SUCCESS 但存在卡 PAYING 的支付单（订单已成交的历史悬挂），对账 CAS 推进为 SUCCESS 后返回。
     * 无可用成交支付单返回 null。
     */
    PaymentOrder findSuccessPaymentOrderForRefund(String orderNo);
}
