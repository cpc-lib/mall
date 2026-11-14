package cc.ivera.payment.domain.repository;

import cc.ivera.payment.domain.model.PaymentDeal;

/**
 * 有效支付成交事实仓储端口。
 */
public interface PaymentDealRepository {

    /**
     * 插入成交事实：唯一键冲突（同 order_no 或同 payment_no）返回 false，其它异常继续抛出。
     */
    boolean insert(PaymentDeal deal);

    PaymentDeal findByOrderNo(String orderNo);

    PaymentDeal findByPaymentNo(String paymentNo);

    boolean existsByOrderNo(String orderNo);
}
