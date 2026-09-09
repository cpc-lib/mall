package cc.ivera.payment.domain.model;

import cc.ivera.payment.domain.enums.PaymentOrderStatus;
import cc.ivera.shared.domain.exception.BizException;
import lombok.Data;

import java.util.Date;

/**
 * 支付单聚合根（t_payment_order）：本地订单 1:N 渠道支付尝试。
 * 一个业务订单允许多次支付尝试，但只允许一笔有效成交支付（SUCCESS）；
 * 其余成功支付进入 DUPLICATE_PAYMENT/LATE_PAYMENT 自动原路退款。
 *
 * <p>状态推进的并发权威闸门为仓储 CAS 条件更新（支付单号行锁/状态 CAS），
 * 本类承载创建规则与守卫；状态流转一律先经守卫再走 CAS，禁止先改后查。</p>
 */
@Data
public class PaymentOrder {

    private Long id;

    private String paymentNo;//商户支付单编号（每次发起支付生成）

    private String orderNo;//商户订单编号

    private String channel;//支付渠道：WXPAY、ALIPAY

    private String channelOrderNo;//渠道侧交易号（微信 transaction_id / 支付宝 trade_no）

    private String codeUrl;//支付二维码连接（NATIVE）

    private Integer requestAmount;//请求支付金额(分)

    private Integer paidAmount;//实际支付金额(分)

    private Integer refundFrozenAmount;//渠道退款冻结金额(分)

    private Integer refundedAmount;//渠道累计已退款金额(分)

    private String status;//支付单状态：CREATED/PAYING/SUCCESS/CLOSED

    private Date expireTime;//支付单过期时间（必须早于等于业务订单过期时间）

    private Date paidTime;//支付成功时间

    private Date createTime;

    private Date updateTime;

    /**
     * 距订单过期不足 60 秒时拒绝发起新支付。
     */
    public static final long MIN_PAY_WINDOW_MS = 60_000L;

    /**
     * 支付单最长有效期：min(order.expire_time, now + 2h)。
     */
    public static final long MAX_PAYMENT_LIVE_MS = 2L * 60L * 60L * 1000L;

    /**
     * 发起支付工厂（CREATED）：临近过期拒绝（距订单过期 &lt;60s）；
     * expire_time = min(order.expire_time, now + 2h)。
     */
    public static PaymentOrder startNew(String paymentNo, String orderNo, String channel,
                                        Integer requestAmount, Date orderExpireTime, Date now) {
        if (orderExpireTime != null && orderExpireTime.getTime() - now.getTime() < MIN_PAY_WINDOW_MS) {
            throw new BizException("订单即将过期，请重新下单，orderNo=" + orderNo);
        }
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo(paymentNo);
        paymentOrder.setOrderNo(orderNo);
        paymentOrder.setChannel(channel);
        paymentOrder.setRequestAmount(requestAmount);
        paymentOrder.setRefundFrozenAmount(0);
        paymentOrder.setRefundedAmount(0);
        paymentOrder.setStatus(PaymentOrderStatus.CREATED.getType());
        paymentOrder.setExpireTime(resolveExpireTime(orderExpireTime, now));
        return paymentOrder;
    }

    /**
     * 兼容补建工厂（PAYING）：历史订单无支付单时补建，作为成交唯一性兜底锚点
     * （重复支付/晚到支付冲正需要一笔可标记成功的支付单）。
     */
    public static PaymentOrder createCompatible(String paymentNo, String orderNo, String channel,
                                                Integer requestAmount, Date orderExpireTime, Date now) {
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo(paymentNo);
        paymentOrder.setOrderNo(orderNo);
        paymentOrder.setChannel(channel);
        paymentOrder.setRequestAmount(requestAmount);
        paymentOrder.setRefundFrozenAmount(0);
        paymentOrder.setRefundedAmount(0);
        paymentOrder.setStatus(PaymentOrderStatus.PAYING.getType());
        paymentOrder.setExpireTime(orderExpireTime != null ? orderExpireTime : now);
        return paymentOrder;
    }

    private static Date resolveExpireTime(Date orderExpireTime, Date now) {
        Date maxLive = new Date(now.getTime() + MAX_PAYMENT_LIVE_MS);
        if (orderExpireTime == null || orderExpireTime.after(maxLive)) {
            return maxLive;
        }
        return orderExpireTime;
    }
}
