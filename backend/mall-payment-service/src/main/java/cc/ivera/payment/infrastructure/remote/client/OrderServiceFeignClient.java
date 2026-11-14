package cc.ivera.payment.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 订单服务内部契约客户端：路径与 {@code OrderInternalController} 保持一一对应。
 */
@FeignClient(name = "mall-order-service", url = "${order.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface OrderServiceFeignClient {

    @GetMapping("/internal/order/payment/{orderNo}")
    R<OrderInfo> getOrderByOrderNo(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/order/payment/create-or-reuse")
    R<OrderInfo> createOrReuseOrder(@RequestBody CreateOrReuseBody body);

    @PostMapping("/internal/order/payment/{orderNo}/code-url")
    R<Object> saveCodeUrl(@PathVariable("orderNo") String orderNo, @RequestBody CodeUrlBody body);

    @GetMapping("/internal/order/payment/{orderNo}/status")
    R<String> getOrderStatus(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/order/status/mark-paying")
    R<Boolean> markPaying(@RequestBody OrderNoBody body);

    @PostMapping("/internal/order/status/close-if-not-pay")
    R<Boolean> closeIfNotPay(@RequestBody CloseIfNotPayBody body);

    @PostMapping("/internal/order/status/close-oversold")
    R<Object> closeOversold(@RequestBody OrderNoBody body);

    record CreateOrReuseBody(Long productId, String paymentType, Long paymentAppId, String paymentChannelCode) {
    }

    record CodeUrlBody(String codeUrl) {
    }

    record OrderNoBody(String orderNo) {
    }

    record CloseIfNotPayBody(String orderNo, OrderStatus target) {
    }
}
