package cc.ivera.refund.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 订单服务内部契约客户端：路径与 {@code OrderInternalController} 保持一一对应。
 */
@FeignClient(name = "mall-order-service", url = "${order.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface OrderServiceFeignClient {

    @GetMapping("/internal/order/refund/{orderNo}")
    R<OrderInfo> findByOrderNo(@PathVariable("orderNo") String orderNo);

    @GetMapping("/internal/order/refund/{orderNo}/items")
    R<List<OrderItem>> listItemsByOrderNo(@PathVariable("orderNo") String orderNo);

    @GetMapping("/internal/order/refund/items/{itemId}")
    R<OrderItem> findItemById(@PathVariable("itemId") Long itemId);

    @PostMapping("/internal/order/refund/quota/{orderNo}/freeze")
    R<Integer> freezeOrderRefund(@PathVariable("orderNo") String orderNo, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/refund/quota/{orderNo}/release")
    R<Integer> releaseOrderRefundFreeze(@PathVariable("orderNo") String orderNo, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/refund/quota/{orderNo}/settle")
    R<Integer> settleOrderRefund(@PathVariable("orderNo") String orderNo, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/refund/quota/{orderNo}/apply-status")
    R<Integer> applyOrderRefundStatus(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/order/refund/quota/{orderNo}/close-if-full-refunded")
    R<Integer> casCloseIfFullRefunded(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/order/refund/quota/item/{itemId}/freeze")
    R<Integer> freezeItemRefund(@PathVariable("itemId") Long itemId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/refund/quota/item/{itemId}/release")
    R<Integer> releaseItemRefundFreeze(@PathVariable("itemId") Long itemId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/refund/quota/item/{itemId}/settle")
    R<Integer> settleItemRefund(@PathVariable("itemId") Long itemId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/refund/quota/item/{itemId}/add-restocked")
    R<Integer> addRestockedQty(@PathVariable("itemId") Long itemId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/refund/status/{orderNo}")
    R<Object> updateRefundStatus(@PathVariable("orderNo") String orderNo, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/status/mark-paying")
    R<Boolean> markPaying(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/status/close-if-not-pay")
    R<Boolean> closeIfNotPay(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/order/status/close-oversold")
    R<Object> closeOversold(@RequestBody Map<String, Object> body);
}
