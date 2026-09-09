package cc.ivera.order.interfaces;

import cc.ivera.order.application.CheckoutService;
import cc.ivera.order.application.ShipmentService;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.interfaces.vo.ShipmentDetailVO;
import cc.ivera.order.interfaces.vo.ShipmentTimelineVO;
import cc.ivera.refund.application.RefundOrderService;
import cc.ivera.refund.interfaces.vo.RefundApplyVO;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端订单履约/物流（V2）：物流详情时间线、确认收货、已付款未发货取消。
 * 物流查询与确认收货均校验订单归属（防水平越权）。
 */
@RestController
@RequestMapping("/api/order")
@CrossOrigin
@Validated
@Api(tags = "订单履约API")
public class OrderShipmentController {

    private final ShipmentService shipmentService;
    private final RefundOrderService refundOrderService;
    private final CheckoutService checkoutService;

    public OrderShipmentController(ShipmentService shipmentService, RefundOrderService refundOrderService,
                                   CheckoutService checkoutService) {
        this.shipmentService = shipmentService;
        this.refundOrderService = refundOrderService;
        this.checkoutService = checkoutService;
    }

    @ApiOperation("查询订单物流详情/时间线")
    @GetMapping("/{orderNo}/shipment")
    public R<ShipmentDetailVO> getShipment(@PathVariable String orderNo) {
        requireOwnership(orderNo);
        OrderShipment shipment = shipmentService.getShipment(orderNo);
        ShipmentDetailVO data = new ShipmentDetailVO();
        data.setOrderNo(orderNo);
        if (shipment == null) {
            data.setShipped(false);
            return R.ok(data).setMessage("订单尚未发货");
        }
        data.setShipped(true);
        data.setShipmentNo(shipment.getShipmentNo());
        data.setLogisticsCompany(shipment.getLogisticsCompany());
        data.setTrackingNo(shipment.getTrackingNo());
        data.setStatus(shipment.getStatus());
        data.setTimeline(buildTimeline(shipment));
        return R.ok(data);
    }

    @ApiOperation("确认收货（物流送达后）")
    @PostMapping("/{orderNo}/confirm-receipt")
    public R<String> confirmReceipt(@PathVariable String orderNo) {
        requireOwnership(orderNo);
        shipmentService.confirmReceipt(orderNo);
        return R.ok("确认收货成功").setMessage("感谢您的确认，交易完成");
    }

    @ApiOperation("已付款未发货取消订单（创建未发货取消退款单，走受理链路）")
    @PostMapping("/{orderNo}/cancel")
    public R<RefundApplyVO> cancelPaidOrder(@PathVariable String orderNo) {
        return R.ok(refundOrderService.cancelPaidOrder(AuthContext.userId(), orderNo))
            .setMessage("取消申请已提交，退款受理后自动原路退回");
    }

    private ShipmentTimelineVO buildTimeline(OrderShipment s) {
        ShipmentTimelineVO timeline = new ShipmentTimelineVO();
        timeline.setSHIPPED(s.getShippedTime());
        timeline.setIN_TRANSIT(s.getInTransitTime());
        timeline.setDELIVERED(s.getDeliveredTime());
        timeline.setRECEIVED(s.getReceivedTime());
        return timeline;
    }

    /**
     * 校验订单存在且属于当前登录用户。
     */
    private void requireOwnership(String orderNo) {
        checkoutService.assertOwnership(AuthContext.userId(), orderNo);
    }
}
