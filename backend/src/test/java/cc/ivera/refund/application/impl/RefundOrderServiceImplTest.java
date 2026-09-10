package cc.ivera.refund.application.impl;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.payment.application.AliPayService;
import cc.ivera.payment.application.wxpay.WxPayRefundFacade;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.product.application.InventoryService;
import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.refund.application.RefundInfoService;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.model.RefundItem;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import cc.ivera.refund.domain.repository.RefundItemRepository;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RefundOrderServiceImpl 特征测试：mock 全部端口，不连真实 DB/Redis/MQ/渠道。
 * 锁定「已付款未发货取消」链路的渠道出口路由：
 * - OFFLINE 支付单（管理员标记付款）：退款本地结转，不调用微信/支付宝渠道退款；
 * - WXPAY 支付单：走微信渠道退款（现状不变）。
 */
class RefundOrderServiceImplTest {

    private RefundOrderRepository refundOrderRepository;
    private RefundItemRepository refundItemRepository;
    private RefundInfoRepository refundInfoRepository;
    private OrderRepository orderRepository;
    private PaymentOrderRepository paymentOrderRepository;
    private InventoryService inventoryService;
    private AliPayService aliPayService;
    private WxPayRefundFacade wxPayRefundFacade;
    private RefundApplicationService refundApplicationService;
    private RefundInfoService refundInfoService;
    private RefundOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        refundOrderRepository = mock(RefundOrderRepository.class);
        refundItemRepository = mock(RefundItemRepository.class);
        refundInfoRepository = mock(RefundInfoRepository.class);
        orderRepository = mock(OrderRepository.class);
        paymentOrderRepository = mock(PaymentOrderRepository.class);
        inventoryService = mock(InventoryService.class);
        aliPayService = mock(AliPayService.class);
        wxPayRefundFacade = mock(WxPayRefundFacade.class);
        refundApplicationService = mock(RefundApplicationService.class);
        refundInfoService = mock(RefundInfoService.class);
        DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(lockTemplate.execute(anyString(), anyLong(), anyLong(), any(Supplier.class))).thenAnswer(inv ->
            ((Supplier<?>) inv.getArgument(3)).get());
        when(tx.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        service = new RefundOrderServiceImpl(refundOrderRepository, refundItemRepository, refundInfoRepository,
            orderRepository, paymentOrderRepository, inventoryService, lockTemplate, tx,
            aliPayService, wxPayRefundFacade, refundApplicationService, refundInfoService);
    }

    private OrderInfo paidWaitShipOrder() {
        OrderInfo order = new OrderInfo();
        order.setOrderNo("ORD1");
        order.setUserId(1L);
        order.setPayStatus("PAID");
        order.setOrderStatus("ACTIVE");
        order.setFulfillmentStatus("WAIT_SHIP");
        order.setPaymentType("微信");
        order.setTotalFee(100);
        return order;
    }

    private OrderItem orderItem() {
        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrderNo("ORD1");
        item.setProductId(20L);
        item.setUnitPrice(100);
        item.setPayAmount(100);
        item.setQuantity(1);
        item.setRefundedQty(0);
        item.setRefundFrozenQty(0);
        item.setRefundedAmount(0);
        item.setRefundFrozenAmount(0);
        return item;
    }

    private PaymentOrder successPayment(String channel) {
        PaymentOrder po = new PaymentOrder();
        po.setPaymentNo("PMO1");
        po.setOrderNo("ORD1");
        po.setChannel(channel);
        po.setStatus("SUCCESS");
        po.setRequestAmount(100);
        po.setPaidAmount(100);
        return po;
    }

    private void stubCancelFlow(PaymentOrder successPayment) {
        OrderInfo order = paidWaitShipOrder();
        OrderItem item = orderItem();
        when(orderRepository.findByOrderNo("ORD1")).thenReturn(order);
        when(orderRepository.listItemsByOrderNo("ORD1")).thenReturn(Collections.singletonList(item));
        when(orderRepository.findItemById(10L)).thenReturn(item);
        when(orderRepository.freezeItemRefund(eq(10L), anyInt(), anyInt())).thenReturn(1);
        when(orderRepository.freezeOrderRefund(eq("ORD1"), anyInt())).thenReturn(1);
        when(paymentOrderRepository.findSuccessPaymentOrderForRefund("ORD1")).thenReturn(successPayment);
        when(paymentOrderRepository.freezeChannelRefund(eq("PMO1"), anyInt())).thenReturn(1);
        when(paymentOrderRepository.findByPaymentNo("PMO1")).thenReturn(successPayment);
        when(refundInfoRepository.findByRefundNo(anyString())).thenReturn(null);
        when(refundItemRepository.listByRefundNoAsc(anyString())).thenReturn(Collections.emptyList());
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setRefundNo("RFD1");
        refundOrder.setPaymentNo("PMO1");
        when(refundOrderRepository.findByRefundNo(anyString())).thenReturn(refundOrder);
    }

    @Test
    void cancelPaidOrder_offlinePayment_settlesLocallyWithoutChannelCall() {
        stubCancelFlow(successPayment("OFFLINE"));

        service.cancelPaidOrder(1L, "ORD1");

        // 线下收款：退款本地结转（置成功，由事件驱动结转），绝不调用真实渠道退款
        verify(refundInfoService).updateRefundToSuccess(anyString(), isNull(), anyString());
        verify(wxPayRefundFacade, never()).executeRefund(any());
        verify(aliPayService, never()).executeRefund(any());
        // 未发货取消受理即补库存
        verify(inventoryService).restockForRefund(anyString(), anyList(), eq(false));
    }

    @Test
    void cancelPaidOrder_wxpayPayment_callsWxChannelRefund() {
        stubCancelFlow(successPayment("WXPAY"));

        service.cancelPaidOrder(1L, "ORD1");

        // 现状行为：真实渠道支付单走微信退款接口
        verify(wxPayRefundFacade).executeRefund(any(RefundInfo.class));
        verify(refundInfoService, never()).updateRefundToSuccess(anyString(), any(), anyString());
    }

    private RefundOrder stubShippedRefundOnlyFlow() {
        OrderInfo order = paidWaitShipOrder();
        order.setFulfillmentStatus("SHIPPED");
        when(orderRepository.findByOrderNo("ORD1")).thenReturn(order);

        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setRefundNo("RFD1");
        refundOrder.setOrderNo("ORD1");
        refundOrder.setUserId(1L);
        refundOrder.setRefundType("REFUND_ONLY");
        refundOrder.setRefundAmount(100);
        refundOrder.setStatus("APPLYING");
        refundOrder.setLegacyApplyStatus("PENDING");
        when(refundOrderRepository.findByRefundNo("RFD1")).thenReturn(refundOrder);

        RefundItem refundItem = new RefundItem();
        refundItem.setId(1L);
        refundItem.setRefundNo("RFD1");
        refundItem.setOrderItemId(10L);
        refundItem.setProductId(20L);
        refundItem.setRefundQty(1);
        refundItem.setRefundAmount(100);
        when(refundItemRepository.listByRefundNoAsc("RFD1")).thenReturn(Collections.singletonList(refundItem));

        PaymentOrder paymentOrder = successPayment("WXPAY");
        when(paymentOrderRepository.findSuccessPaymentOrderForRefund("ORD1")).thenReturn(paymentOrder);
        when(paymentOrderRepository.freezeChannelRefund("PMO1", 100)).thenReturn(1);
        when(paymentOrderRepository.findByPaymentNo("PMO1")).thenReturn(paymentOrder);
        when(refundInfoRepository.findByRefundNo("RFD1")).thenReturn(null);
        return refundOrder;
    }

    @Test
    void listAll_marksShippedRefundOnlyAsGoodsDispositionRequired() {
        RefundOrder refundOrder = stubShippedRefundOnlyFlow();
        when(refundOrderRepository.listAllCreateTimeDesc()).thenReturn(Collections.singletonList(refundOrder));

        assertTrue(service.listAll().get(0).isGoodsDispositionRequired());
    }

    @Test
    void acceptShippedRefundOnly_requiresAdminGoodsDisposition() {
        stubShippedRefundOnlyFlow();

        assertThrows(BizException.class, () -> service.accept("RFD1", "管理员受理", null));

        verify(inventoryService, never()).restockForRefund(anyString(), anyList(), anyBoolean());
        verify(inventoryService, never()).writeOffLostForRefund(anyString(), anyList(), anyBoolean());
        verify(paymentOrderRepository, never()).freezeChannelRefund(anyString(), anyInt());
        verify(wxPayRefundFacade, never()).executeRefund(any());
    }

    @Test
    void acceptShippedRefundOnly_lost_movesLockedToLostThenRefunds() {
        RefundOrder refundOrder = stubShippedRefundOnlyFlow();

        service.accept("RFD1", "物流确认丢失", "LOST");

        verify(inventoryService).writeOffLostForRefund(eq("RFD1"), anyList(), eq(false));
        verify(inventoryService, never()).restockForRefund(anyString(), anyList(), anyBoolean());
        verify(wxPayRefundFacade).executeRefund(any(RefundInfo.class));
        org.junit.jupiter.api.Assertions.assertEquals("LOST", refundOrder.getGoodsDisposition());
    }

    @Test
    void acceptShippedRefundOnly_recovered_returnsLockedToAvailableThenRefunds() {
        RefundOrder refundOrder = stubShippedRefundOnlyFlow();

        service.accept("RFD1", "商品已退回仓库", "RECOVERED");

        verify(inventoryService).restockForRefund(eq("RFD1"), anyList(), eq(false));
        verify(inventoryService, never()).writeOffLostForRefund(anyString(), anyList(), anyBoolean());
        verify(wxPayRefundFacade).executeRefund(any(RefundInfo.class));
        org.junit.jupiter.api.Assertions.assertEquals("RECOVERED", refundOrder.getGoodsDisposition());
    }
}
