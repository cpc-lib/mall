package cc.ivera.payment.domain.model;

import cc.ivera.payment.domain.enums.PaymentOrderStatus;
import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * payment 上下文支付单聚合根纯单元测试：不连数据库/Redis/MQ。
 * 锁定从 PaymentOrderServiceImpl 等价搬入的创建规则：
 * 距订单过期不足 60 秒拒绝发起；expireTime = min(订单过期时间, now + 2h)；兼容补建单为 PAYING。
 */
class PaymentOrderAggregateTest {

    private static final Date NOW = new Date(1_000_000_000_000L);

    @Test
    void startNewRejectsWhenOrderExpiringWithin60Seconds() {
        Date soonExpire = new Date(NOW.getTime() + PaymentOrder.MIN_PAY_WINDOW_MS - 1L);
        BizException ex = assertThrows(BizException.class,
            () -> PaymentOrder.startNew("PAY1", "ORD1", "WXPAY", 9900, soonExpire, NOW));
        assertTrue(ex.getMessage().contains("ORD1"));
    }

    @Test
    void startNewAllowsExactly60SecondWindow() {
        Date boundaryExpire = new Date(NOW.getTime() + PaymentOrder.MIN_PAY_WINDOW_MS);
        PaymentOrder order = PaymentOrder.startNew("PAY1", "ORD1", "WXPAY", 9900, boundaryExpire, NOW);
        assertEquals(boundaryExpire, order.getExpireTime());
    }

    @Test
    void startNewCapsExpireTimeAtTwoHoursWhenOrderExpiresLater() {
        Date farFuture = new Date(NOW.getTime() + 10L * 60L * 60L * 1000L);
        PaymentOrder order = PaymentOrder.startNew("PAY1", "ORD1", "WXPAY", 9900, farFuture, NOW);
        assertEquals(new Date(NOW.getTime() + PaymentOrder.MAX_PAYMENT_LIVE_MS), order.getExpireTime());
    }

    @Test
    void startNewUsesOrderExpireTimeWhenSoonerThanTwoHours() {
        Date orderExpire = new Date(NOW.getTime() + 30L * 60L * 1000L);
        PaymentOrder order = PaymentOrder.startNew("PAY1", "ORD1", "WXPAY", 9900, orderExpire, NOW);
        assertEquals(orderExpire, order.getExpireTime());
    }

    @Test
    void startNewDefaultsExpireTimeToTwoHoursWhenOrderExpireNull() {
        PaymentOrder order = PaymentOrder.startNew("PAY1", "ORD1", "ALIPAY", 5000, null, NOW);
        assertEquals(new Date(NOW.getTime() + PaymentOrder.MAX_PAYMENT_LIVE_MS), order.getExpireTime());
    }

    @Test
    void startNewInitializesCreatedStateAndZeroRefundCounters() {
        Date orderExpire = new Date(NOW.getTime() + 3_600_000L);
        PaymentOrder order = PaymentOrder.startNew("PAY1", "ORD1", "WXPAY", 9900, orderExpire, NOW);
        assertEquals("PAY1", order.getPaymentNo());
        assertEquals("ORD1", order.getOrderNo());
        assertEquals("WXPAY", order.getChannel());
        assertEquals(Integer.valueOf(9900), order.getRequestAmount());
        assertEquals(PaymentOrderStatus.CREATED.getType(), order.getStatus());
        assertEquals(Integer.valueOf(0), order.getRefundFrozenAmount());
        assertEquals(Integer.valueOf(0), order.getRefundedAmount());
    }

    @Test
    void createCompatibleBuildsPayingOrderWithOrderExpireTime() {
        Date orderExpire = new Date(NOW.getTime() + 3_600_000L);
        PaymentOrder order = PaymentOrder.createCompatible("PAY2", "ORD2", "ALIPAY", 8800, orderExpire, NOW);
        assertEquals(PaymentOrderStatus.PAYING.getType(), order.getStatus());
        assertEquals(orderExpire, order.getExpireTime());
        assertEquals("PAY2", order.getPaymentNo());
        assertEquals("ORD2", order.getOrderNo());
        assertEquals("ALIPAY", order.getChannel());
        assertEquals(Integer.valueOf(8800), order.getRequestAmount());
        assertEquals(Integer.valueOf(0), order.getRefundFrozenAmount());
        assertEquals(Integer.valueOf(0), order.getRefundedAmount());
    }

    @Test
    void createCompatibleFallsBackToNowWhenOrderExpireNull() {
        PaymentOrder order = PaymentOrder.createCompatible("PAY2", "ORD2", "WXPAY", 8800, null, NOW);
        assertEquals(NOW, order.getExpireTime());
    }
}
