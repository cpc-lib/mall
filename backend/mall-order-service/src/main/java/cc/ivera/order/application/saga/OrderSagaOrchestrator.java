package cc.ivera.order.application.saga;

import cc.ivera.order.application.dto.CheckoutRequest;
import cc.ivera.order.application.impl.CheckoutTxService;
import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下单 Saga 编排：先落 Saga 状态（独立事务），再执行本地落单事务，最后收敛 Saga 状态。
 * 库存预占仍在落单事务内执行，失败时由事务回滚与 afterRollback 释放保证 AGENTS 5.1 不变量。
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderSagaService orderSagaService;
    private final CheckoutTxService checkoutTxService;
    private final ObjectMapper objectMapper;

    public OrderSagaOrchestrator(OrderSagaService orderSagaService,
                                 CheckoutTxService checkoutTxService,
                                 ObjectMapper objectMapper) {
        this.orderSagaService = orderSagaService;
        this.checkoutTxService = checkoutTxService;
        this.objectMapper = objectMapper;
    }

    public OrderDetailVO checkout(Long userId, CheckoutRequest request) {
        String orderNo = OrderNoUtils.getOrderNo();
        String sagaNo = "SAGA-" + orderNo;
        orderSagaService.start(orderNo, payload(userId, request));
        try {
            OrderDetailVO result = checkoutTxService.create(userId, request, orderNo);
            orderSagaService.succeed(sagaNo);
            return result;
        } catch (RuntimeException e) {
            log.warn("下单 Saga 失败，进入补偿收敛，orderNo={}", orderNo, e);
            orderSagaService.fail(sagaNo, OrderSagaService.STEP_CREATE_ORDER);
            throw e;
        }
    }

    private String payload(Long userId, CheckoutRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("paymentType", request.getPaymentType());
        payload.put("paymentAppId", request.getPaymentAppId());
        payload.put("addressId", request.getAddressId());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"userId\":" + userId + "}";
        }
    }
}
