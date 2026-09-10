package cc.ivera.order.interfaces.job;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelOrderStatusDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 超时关单 DB 最终一致性兜底测试。
 * 锁定 Scheduler 的核心契约：扫描超时未支付订单、复用既有渠道查单/关单链路，
 * 且单笔失败不能阻塞同批次后续订单继续收敛。
 */
class TimeoutOrderCloseSchedulerTest {

    private OrderInfoService orderInfoService;
    private ChannelOrderStatusDispatcher channelOrderStatusDispatcher;
    private TimeoutOrderCloseScheduler scheduler;

    @BeforeEach
    void setUp() {
        orderInfoService = mock(OrderInfoService.class);
        channelOrderStatusDispatcher = mock(ChannelOrderStatusDispatcher.class);
        scheduler = new TimeoutOrderCloseScheduler(orderInfoService, channelOrderStatusDispatcher);
        ReflectionTestUtils.setField(scheduler, "orderExpireMinutes", 3L);
    }

    @Test
    void scan_dispatchesAllTimeoutOrdersEvenWhenOneFails() {
        OrderInfo first = order("ORD-FAIL", "微信");
        OrderInfo second = order("ORD-OK", "支付宝");
        when(orderInfoService.listTimeoutNotPayOrders(any(Date.class)))
            .thenReturn(Arrays.asList(first, second));
        doThrow(new RuntimeException("channel unavailable"))
            .when(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-FAIL");

        scheduler.scan();

        verify(orderInfoService).listTimeoutNotPayOrders(any(Date.class));
        verify(channelOrderStatusDispatcher).checkOrderStatus("微信", "ORD-FAIL");
        verify(channelOrderStatusDispatcher).checkOrderStatus("支付宝", "ORD-OK");
    }

    @Test
    void scan_noTimeoutOrders_doesNotCallChannel() {
        when(orderInfoService.listTimeoutNotPayOrders(any(Date.class)))
            .thenReturn(Collections.emptyList());

        scheduler.scan();

        verifyNoInteractions(channelOrderStatusDispatcher);
    }

    private OrderInfo order(String orderNo, String paymentType) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(orderNo);
        orderInfo.setPaymentType(paymentType);
        return orderInfo;
    }
}
