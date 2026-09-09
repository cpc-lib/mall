package cc.ivera.payment.application.impl;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.PaymentOrderService;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.refund.application.ExceptionRefundService;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PaymentSuccessServiceImpl 特征测试：mock 端口，不连真实 DB/Redis。
 * 锁定管理员标记付款（线下收款）行为：补记 OFFLINE SUCCESS 支付单 + 订单 NOTPAY→SUCCESS。
 */
class PaymentSuccessServiceImplTest {

    private OrderInfoService orderInfoService;
    private PaymentOrderRepository paymentOrderRepository;
    private PaymentOrderService paymentOrderService;
    private ExceptionRefundService exceptionRefundService;
    private DistributedLockTemplate lockTemplate;
    private TransactionTemplate tx;
    private PaymentSuccessServiceImpl service;

    @BeforeEach
    void setUp() {
        orderInfoService = mock(OrderInfoService.class);
        paymentOrderRepository = mock(PaymentOrderRepository.class);
        paymentOrderService = mock(PaymentOrderService.class);
        exceptionRefundService = mock(ExceptionRefundService.class);
        lockTemplate = mock(DistributedLockTemplate.class);
        tx = mock(TransactionTemplate.class);
        service = new PaymentSuccessServiceImpl(orderInfoService, paymentOrderRepository,
            paymentOrderService, exceptionRefundService, lockTemplate, tx);
        // 锁与事务模板均同步直执行
        when(lockTemplate.execute(anyString(), anyLong(), anyLong(), any(Supplier.class))).thenAnswer(inv ->
            ((Supplier<?>) inv.getArgument(3)).get());
        when(tx.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
    }

    private OrderInfo order(String orderNo, String legacyStatus) {
        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setLegacyStatus(legacyStatus);
        order.setTotalFee(100);
        order.setExpireTime(new Date(System.currentTimeMillis() + 30 * 60 * 1000L));
        order.setPaymentType("微信");
        return order;
    }

    @Test
    void markOfflinePaid_recordsOfflineSuccessPaymentOrderAndAdvancesOrder() {
        OrderInfo order = order("ORD1", OrderStatus.NOTPAY.getType());
        when(orderInfoService.getOrderByOrderNo("ORD1")).thenReturn(order);
        when(paymentOrderRepository.findActiveByOrderNoAndChannel(eq("ORD1"), eq(PaymentConfigGateway.CHANNEL_OFFLINE))).thenReturn(null);
        when(paymentOrderRepository.findLatestSuccessByOrderNoAndChannel(eq("ORD1"), eq(PaymentConfigGateway.CHANNEL_OFFLINE))).thenReturn(null);
        when(paymentOrderService.markSuccess(anyString(), isNull(), eq(100))).thenReturn(true);
        when(orderInfoService.updateStatusByOrderNoIfStatus(eq("ORD1"), eq(OrderStatus.NOTPAY), eq(OrderStatus.SUCCESS))).thenReturn(true);

        boolean result = service.markOfflinePaid("ORD1");

        assertTrue(result);
        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderRepository).save(captor.capture());
        PaymentOrder saved = captor.getValue();
        assertEquals(PaymentConfigGateway.CHANNEL_OFFLINE, saved.getChannel());
        assertEquals("ORD1", saved.getOrderNo());
        assertEquals(100, saved.getRequestAmount());
        // 兼容补建工厂直接为 PAYING，markSuccess CAS 推进为 SUCCESS
        assertEquals("PAYING", saved.getStatus());
        verify(paymentOrderService).markSuccess(eq(saved.getPaymentNo()), isNull(), eq(100));
        // 成交收口：关闭同订单其它渠道（微信/支付宝）活跃支付单
        verify(paymentOrderRepository).closeActiveByOrderNoExceptPaymentNo(eq("ORD1"), eq(saved.getPaymentNo()));
        verify(exceptionRefundService, never()).duplicatePayment(anyString());
        verify(exceptionRefundService, never()).latePayment(anyString());
    }

    @Test
    void markOfflinePaid_orderMissing_throws() {
        when(orderInfoService.getOrderByOrderNo("ORD404")).thenReturn(null);
        assertThrows(BizException.class, () -> service.markOfflinePaid("ORD404"));
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void markOfflinePaid_concurrentCasLoses_returnsFalse() {
        OrderInfo order = order("ORD2", OrderStatus.NOTPAY.getType());
        when(orderInfoService.getOrderByOrderNo("ORD2")).thenReturn(order);
        when(paymentOrderRepository.findActiveByOrderNoAndChannel(anyString(), anyString())).thenReturn(null);
        when(paymentOrderRepository.findLatestSuccessByOrderNoAndChannel(anyString(), anyString())).thenReturn(null);
        when(paymentOrderService.markSuccess(anyString(), isNull(), eq(100))).thenReturn(true);
        // 订单 CAS 失败：并发方已成交
        when(orderInfoService.updateStatusByOrderNoIfStatus(anyString(), any(), any())).thenReturn(false);

        boolean result = service.markOfflinePaid("ORD2");

        assertFalse(result);
        verify(paymentOrderRepository, never()).closeActiveByOrderNoExceptPaymentNo(anyString(), anyString());
    }
}
