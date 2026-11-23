package cc.ivera.order.interfaces;

import cc.ivera.order.application.CheckoutService;
import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.application.ShipmentService;
import cc.ivera.order.domain.enums.OrderLifecycleStatus;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.order.application.vo.ShipmentDetailVO;
import cc.ivera.payment.application.ChannelPaymentQueryApi;
import cc.ivera.payment.application.PaymentSuccessService;
import cc.ivera.payment.domain.enums.PayStatus;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 管理员订单履约（V2）：待发货订单列表 + 模拟物流发货 + 全部订单管理 + 异常处理。
 */
@RestController
@RequestMapping("/api/admin/order")
@CrossOrigin
@Validated
@Tag(name = "管理员订单履约API")
public class AdminOrderShipmentController {

    private final ShipmentService shipmentService;
    private final PaymentOrderQueryApi paymentOrderRepository;
    private final CheckoutService checkoutService;
    private final OrderInfoService orderInfoService;
    private final ChannelPaymentQueryApi channelPaymentQueryDispatcher;
    private final PaymentSuccessService paymentSuccessService;

    public AdminOrderShipmentController(ShipmentService shipmentService,
                                        PaymentOrderQueryApi paymentOrderRepository,
                                        CheckoutService checkoutService,
                                        OrderInfoService orderInfoService,
                                        ChannelPaymentQueryApi channelPaymentQueryDispatcher,
                                        PaymentSuccessService paymentSuccessService) {
        this.shipmentService = shipmentService;
        this.paymentOrderRepository = paymentOrderRepository;
        this.checkoutService = checkoutService;
        this.orderInfoService = orderInfoService;
        this.channelPaymentQueryDispatcher = channelPaymentQueryDispatcher;
        this.paymentSuccessService = paymentSuccessService;
    }

    @Operation(summary = "待发货订单列表（已支付未发货，分页）")
    @GetMapping("/wait-ship")
    public R<PageVO<OrderInfo>> waitShipList(@RequestParam(value = "page", defaultValue = "1") int page,
                                             @RequestParam(value = "size", defaultValue = "10") int size) {
        return R.ok(orderInfoService.listWaitShipOrders(page, size));
    }

    @Operation(summary = "全部订单列表（管理员，可选状态/订单号/用户编号/下单时间范围筛选，分页）")
    @GetMapping("/all")
    public R<PageVO<OrderDetailVO>> allOrders(
        @RequestParam(value = "payStatus", required = false) String payStatus,
        @RequestParam(value = "orderStatus", required = false) String orderStatus,
        @RequestParam(value = "fulfillmentStatus", required = false) String fulfillmentStatus,
        @RequestParam(value = "orderNo", required = false) String orderNo,
        @RequestParam(value = "userId", required = false) String userId,
        @RequestParam(value = "startTime", required = false) String startTime,
        @RequestParam(value = "endTime", required = false) String endTime,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(checkoutService.listAllOrders(payStatus, orderStatus, fulfillmentStatus,
            orderNo, userId, startTime, endTime, page, size));
    }

    @Operation(summary = "强制关单（仅未支付/支付中订单，释放预占库存）")
    @PostMapping("/{orderNo}/force-close")
    public R<?> forceClose(@PathVariable String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) throw new BizException("订单不存在");
        String ps = order.getPayStatus();
        if (!PayStatus.UNPAID.getType().equals(ps) && !PayStatus.PAYING.getType().equals(ps))
            throw new BizException("仅未支付/支付中订单可强制关单，当前支付状态：" + ps);
        // 已被超时调度器或前次操作关闭的订单直接返回成功（幂等）。
        if (OrderLifecycleStatus.CLOSED.getType().equals(order.getOrderStatus())) {
            return R.ok().setMessage("订单已关闭（幂等），无需重复操作");
        }
        // 关单前必须先查成交事实：支付刚刚成交但 MQ 尚未消费的窗口期内，payStatus 仍为 PAYING，
        // 必须以 t_payment_deal 为权威，禁止"已收款却本地关单"。
        if (paymentOrderRepository.hasSuccessfulPayment(orderNo)) {
            orderInfoService.markPaid(orderNo);
            return R.ok().setMessage("订单已支付成交，已标记为已支付状态，禁止关单");
        }
        boolean ok = orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CLOSED);
        if (!ok) throw new BizException("关单失败：订单状态可能已被并发修改，请刷新后重试");
        return R.ok().setMessage("订单已强制关闭，预占库存已释放");
    }

    @Operation(summary = "手动标记支付成功（线下收款，仅未支付/支付中订单；补记 OFFLINE 支付单，该单退款本地结转）")
    @PostMapping("/{orderNo}/mark-paid")
    public R<?> markPaid(@PathVariable String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) throw new BizException("订单不存在");
        String ps = order.getPayStatus();
        if (!PayStatus.UNPAID.getType().equals(ps) && !PayStatus.PAYING.getType().equals(ps))
            throw new BizException("仅未支付/支付中订单可标记支付，当前支付状态：" + ps);
        boolean ok = paymentSuccessService.markOfflinePaid(orderNo);
        if (!ok) throw new BizException("标记失败：订单状态可能已被并发修改，请刷新后重试");
        return R.ok().setMessage("订单已标记为支付成功，预占库存已提交");
    }

    @Operation(summary = "订单物流信息（管理端按需查询，含时间线）")
    @GetMapping("/{orderNo}/shipment")
    public R<ShipmentDetailVO> shipment(@PathVariable String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        ShipmentDetailVO data = shipmentService.getShipmentDetail(orderNo);
        if (Boolean.FALSE.equals(data.getShipped())) {
            return R.ok(data).setMessage("订单尚未发货");
        }
        return R.ok(data);
    }

    @Operation(summary = "模拟发货（对接模拟物流商创建运单）")
    @PostMapping("/{orderNo}/ship")
    public R<OrderShipment> ship(@PathVariable String orderNo) {
        OrderShipment shipment = shipmentService.ship(orderNo);
        return R.ok(shipment).setMessage("发货成功，运单号 " + shipment.getTrackingNo());
    }

    @Operation(summary = "支付尝试记录（本订单全部渠道支付单：渠道/状态/渠道交易号/金额/时间）")
    @GetMapping("/{orderNo}/payment-orders")
    public R<List<PaymentOrder>> paymentOrders(
        @PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return R.ok(paymentOrderRepository.listByOrderNoAsc(orderNo));
    }

    @Operation(summary = "渠道订单查询（主动向微信/支付宝查单，已支付成功则同步本地订单；不自动关单）")
    @GetMapping("/{orderNo}/channel-query")
    public R<ChannelOrderQueryVO> channelQuery(
        @PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }

        return R.ok(channelPaymentQueryDispatcher.query(order.getPaymentType(), orderNo));
    }
}
