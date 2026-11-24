package cc.ivera.order.interfaces;

import cc.ivera.api.order.OrderStatusCommandApi;
import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.application.dto.OrderCloseIfNotPayRequest;
import cc.ivera.order.application.dto.OrderCodeUrlRequest;
import cc.ivera.order.application.dto.OrderCreateOrReuseRequest;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.infrastructure.gateway.OrderInternalApiAdapter;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/order")
public class OrderInternalController {
    private final OrderInfoService orderInfoService;
    private final OrderStatusCommandApi orderStatusCommandApi;
    private final OrderInternalApiAdapter orderInternalApiAdapter;

    public OrderInternalController(OrderInfoService orderInfoService,
                                   OrderStatusCommandApi orderStatusCommandApi,
                                   OrderInternalApiAdapter orderInternalApiAdapter) {
        this.orderInfoService = orderInfoService;
        this.orderStatusCommandApi = orderStatusCommandApi;
        this.orderInternalApiAdapter = orderInternalApiAdapter;
    }

    @GetMapping("/payment/{orderNo}")
    public R<OrderInfo> getByOrderNo(@PathVariable String orderNo) {
        return R.ok(orderInfoService.getOrderByOrderNo(orderNo));
    }

    @PostMapping("/payment/create-or-reuse")
    public R<OrderInfo> createOrReuse(@RequestBody OrderCreateOrReuseRequest request) {
        return R.ok(orderInfoService.createOrReuseOrder(request.getProductId(), request.getPaymentType(),
            request.getPaymentAppId(), request.getPaymentChannelCode()));
    }

    @PostMapping("/payment/{orderNo}/code-url")
    public R<?> saveCodeUrl(@PathVariable String orderNo, @RequestBody OrderCodeUrlRequest request) {
        orderInfoService.saveCodeUrl(orderNo, request.getCodeUrl());
        return R.ok();
    }

    @GetMapping("/payment/{orderNo}/status")
    public R<String> getStatus(@PathVariable String orderNo) {
        return R.ok(orderInfoService.getOrderStatus(orderNo));
    }

    @PostMapping("/status/close-if-not-pay")
    public R<Boolean> closeIfNotPay(@RequestBody OrderCloseIfNotPayRequest request) {
        return R.ok(orderStatusCommandApi.closeIfNotPay(request.getOrderNo(), request.getTarget()));
    }

    @PostMapping("/status/mark-paying")
    public R<Boolean> markPaying(@RequestBody OrderCloseIfNotPayRequest request) {
        return R.ok(orderStatusCommandApi.markPaying(request.getOrderNo()));
    }

    @PostMapping("/status/close-oversold")
    public R<?> closeOversold(@RequestBody OrderCloseIfNotPayRequest request) {
        orderStatusCommandApi.closeOversold(request.getOrderNo());
        return R.ok();
    }

    @GetMapping("/refund/{orderNo}")
    public R<OrderInfo> getRefundOrder(@PathVariable String orderNo) {
        return R.ok(orderInternalApiAdapter.findByOrderNo(orderNo));
    }

    @PostMapping("/refund/batch")
    public R<List<OrderInfo>> getRefundOrders(@RequestBody List<String> orderNos) {
        return R.ok(orderInternalApiAdapter.findByOrderNos(orderNos));
    }

    @GetMapping("/refund/{orderNo}/items")
    public R<List<OrderItem>> listRefundItems(@PathVariable String orderNo) {
        return R.ok(orderInternalApiAdapter.listItemsByOrderNo(orderNo));
    }

    @GetMapping("/refund/items/{itemId}")
    public R<OrderItem> getRefundItem(@PathVariable Long itemId) {
        return R.ok(orderInternalApiAdapter.findItemById(itemId));
    }

    @PostMapping("/refund/quota/{orderNo}/freeze")
    public R<Integer> freezeOrderRefund(@PathVariable String orderNo, @RequestBody AmountRequest request) {
        return R.ok(orderInternalApiAdapter.freezeOrderRefund(orderNo, request.amount()));
    }

    @PostMapping("/refund/quota/{orderNo}/release")
    public R<Integer> releaseOrderRefundFreeze(@PathVariable String orderNo, @RequestBody AmountRequest request) {
        return R.ok(orderInternalApiAdapter.releaseOrderRefundFreeze(orderNo, request.amount()));
    }

    @PostMapping("/refund/quota/{orderNo}/settle")
    public R<Integer> settleOrderRefund(@PathVariable String orderNo, @RequestBody AmountRequest request) {
        return R.ok(orderInternalApiAdapter.settleOrderRefund(orderNo, request.amount()));
    }

    @PostMapping("/refund/quota/{orderNo}/apply-status")
    public R<Integer> applyOrderRefundStatus(@PathVariable String orderNo) {
        return R.ok(orderInternalApiAdapter.applyOrderRefundStatus(orderNo));
    }

    @PostMapping("/refund/quota/{orderNo}/close-if-full-refunded")
    public R<Integer> casCloseIfFullRefunded(@PathVariable String orderNo) {
        return R.ok(orderInternalApiAdapter.casCloseIfFullRefunded(orderNo));
    }

    @PostMapping("/refund/quota/item/{itemId}/freeze")
    public R<Integer> freezeItemRefund(@PathVariable Long itemId, @RequestBody ItemQuotaRequest request) {
        return R.ok(orderInternalApiAdapter.freezeItemRefund(itemId, request.qty(), request.amount()));
    }

    @PostMapping("/refund/quota/item/{itemId}/release")
    public R<Integer> releaseItemRefundFreeze(@PathVariable Long itemId, @RequestBody ItemQuotaRequest request) {
        return R.ok(orderInternalApiAdapter.releaseItemRefundFreeze(itemId, request.qty(), request.amount()));
    }

    @PostMapping("/refund/quota/item/{itemId}/settle")
    public R<Integer> settleItemRefund(@PathVariable Long itemId, @RequestBody ItemQuotaRequest request) {
        return R.ok(orderInternalApiAdapter.settleItemRefund(itemId, request.qty(), request.amount()));
    }

    @PostMapping("/refund/quota/item/{itemId}/add-restocked")
    public R<Integer> addRestockedQty(@PathVariable Long itemId, @RequestBody QtyRequest request) {
        return R.ok(orderInternalApiAdapter.addRestockedQty(itemId, request.qty()));
    }

    @PostMapping("/refund/status/{orderNo}")
    public R<?> updateRefundStatus(@PathVariable String orderNo, @RequestBody StatusRequest request) {
        orderInternalApiAdapter.updateLegacyStatus(orderNo, request.status());
        return R.ok();
    }

    public record AmountRequest(Integer amount) {
    }

    public record ItemQuotaRequest(Integer qty, Integer amount) {
    }

    public record QtyRequest(Integer qty) {
    }

    public record StatusRequest(OrderStatus status) {
    }
}
