package cc.ivera.payment.application.impl;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.PaymentOrderService;
import cc.ivera.payment.domain.enums.PaymentOrderStatus;
import cc.ivera.payment.domain.model.PaymentDeal;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentDealRepository;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.shared.domain.mq.LocalMessageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * PaymentSuccessServiceImpl 成交状态机 Mockito 单测。
 * 覆盖：渠道交易号幂等、首次有效成交（关其它活跃 + 发事件）、同 paymentNo 幂等、
 * 不同 paymentNo 重复支付检测（发 DUPLICATE）。
 */
class PaymentSuccessServiceImplTest {

    private final OrderPaymentApi orderApi = mock(OrderPaymentApi.class);
    private final PaymentOrderRepository orderRepo = mock(PaymentOrderRepository.class);
    private final PaymentOrderService orderService = mock(PaymentOrderService.class);
    private final PaymentDealRepository dealRepo = mock(PaymentDealRepository.class);
    private final LocalMessageService messageService = mock(LocalMessageService.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    private PaymentSuccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentSuccessServiceImpl(orderApi, orderRepo, orderService, dealRepo, messageService, tx,
            new SimpleMeterRegistry());
    }

    // ==================== 正常成功：关其它渠道活跃 + 发 PAYMENT_SUCCEEDED ====================

    @Test
    void firstDealInsertsAndClosesOtherActivesAndPublishes() {
        OrderInfo order = order("ORD-1", 1000);
        PaymentOrder activeOrder = paymentOrder("PAY-1", "ORD-1", "WXPAY", null, PaymentOrderStatus.PAYING);

        when(orderRepo.listByChannelAndChannelOrderNos(anyString(), anyCollection())).thenReturn(List.of());
        when(orderRepo.findActiveByOrderNoAndChannel("ORD-1", "WXPAY")).thenReturn(activeOrder);
        when(orderService.markSuccess("PAY-1", "WX-CH-1", 1000)).thenReturn(true);
        when(dealRepo.insert(any(PaymentDeal.class))).thenReturn(true);

        boolean result = service.handlePaymentSuccess(order, "WXPAY", "WX-CH-1", 1000);

        assertTrue(result);
        verify(orderRepo).closeActiveByOrderNoExceptPaymentNo("ORD-1", "PAY-1");
        verify(messageService).saveAndPublishAfterCommit(anyString(), anyString(), any());
    }

    // ==================== 渠道交易号已绑定 → 幂等 ====================

    @Test
    void boundChannelOrderNoIsIdempotent() {
        OrderInfo order = order("ORD-1", 1000);
        PaymentOrder alreadyBound = paymentOrder("PAY-0", "ORD-1", "WXPAY", "WX-CH-1", PaymentOrderStatus.SUCCESS);

        when(orderRepo.listByChannelAndChannelOrderNos("WXPAY", List.of("WX-CH-1")))
            .thenReturn(List.of(alreadyBound));

        boolean result = service.handlePaymentSuccess(order, "WXPAY", "WX-CH-1", 1000);

        assertFalse(result);
        // 后续仓储都不该被调用
        verifyNoMoreInteractions(orderService, dealRepo);
    }

    // ==================== CAS 成功 + deal insert 失败（同 paymentNo） → 幂等 ====================

    @Test
    void existingDealSamePaymentNoIsIdempotent() {
        OrderInfo order = order("ORD-1", 1000);
        PaymentOrder activeOrder = paymentOrder("PAY-1", "ORD-1", "WXPAY", null, PaymentOrderStatus.PAYING);
        PaymentDeal existingDeal = new PaymentDeal();
        existingDeal.setPaymentNo("PAY-1");

        when(orderRepo.listByChannelAndChannelOrderNos(anyString(), anyCollection())).thenReturn(List.of());
        when(orderRepo.findActiveByOrderNoAndChannel("ORD-1", "WXPAY")).thenReturn(activeOrder);
        when(orderService.markSuccess("PAY-1", "WX-CH-1", 1000)).thenReturn(true);
        when(dealRepo.insert(any(PaymentDeal.class))).thenReturn(false); // 唯一键冲突
        when(dealRepo.findByOrderNo("ORD-1")).thenReturn(existingDeal); // 同 paymentNo

        boolean result = service.handlePaymentSuccess(order, "WXPAY", "WX-CH-1", 1000);

        assertFalse(result);
        verify(messageService, never()).saveAndPublishAfterCommit(anyString(), anyString(), any());
    }

    // ==================== CAS 成功 + deal insert 失败（不同 paymentNo）→ 重复支付 ====================

    @Test
    void existingDealDifferentPaymentNoDetectsDuplicate() {
        OrderInfo order = order("ORD-1", 1000);
        PaymentOrder activeOrder = paymentOrder("PAY-2", "ORD-1", "WXPAY", null, PaymentOrderStatus.PAYING);
        PaymentDeal existingDeal = new PaymentDeal();
        existingDeal.setPaymentNo("PAY-1"); // 不同

        when(orderRepo.listByChannelAndChannelOrderNos(anyString(), anyCollection())).thenReturn(List.of());
        when(orderRepo.findActiveByOrderNoAndChannel("ORD-1", "WXPAY")).thenReturn(activeOrder);
        when(orderService.markSuccess("PAY-2", "WX-CH-2", 1000)).thenReturn(true);
        when(dealRepo.insert(any(PaymentDeal.class))).thenReturn(false);
        when(dealRepo.findByOrderNo("ORD-1")).thenReturn(existingDeal);

        boolean result = service.handlePaymentSuccess(order, "WXPAY", "WX-CH-2", 1000);

        assertTrue(result);
        verify(messageService).saveAndPublishAfterCommit(
            anyString(), eq("PAY-2"), any()); // DUPLICATE 事件
    }

    // ==================== helpers ====================

    private static OrderInfo order(String orderNo, int totalFee) {
        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setTotalFee(totalFee);
        order.setExpireTime(new Date(System.currentTimeMillis() + 3600_000));
        order.setPaymentType("WXPAY");
        return order;
    }

    private static PaymentOrder paymentOrder(String paymentNo, String orderNo, String channel,
                                              String channelOrderNo, PaymentOrderStatus status) {
        PaymentOrder po = new PaymentOrder();
        po.setPaymentNo(paymentNo);
        po.setOrderNo(orderNo);
        po.setChannel(channel);
        po.setChannelOrderNo(channelOrderNo);
        po.setStatus(status.getType());
        return po;
    }
}
