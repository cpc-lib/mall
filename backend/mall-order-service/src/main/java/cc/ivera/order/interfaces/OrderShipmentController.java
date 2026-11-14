package cc.ivera.order.interfaces;

import cc.ivera.order.application.CheckoutService;
import cc.ivera.order.application.ShipmentService;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.application.vo.ShipmentDetailVO;
import cc.ivera.order.application.vo.ShipmentTimelineVO;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "订单履约API")
public class OrderShipmentController {

    private final ShipmentService shipmentService;
    private final CheckoutService checkoutService;

    public OrderShipmentController(ShipmentService shipmentService, CheckoutService checkoutService) {
        this.shipmentService = shipmentService;
        this.checkoutService = checkoutService;
    }

    @Operation(summary = "查询订单物流详情/时间线")
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

    @Operation(summary = "确认收货（物流送达后）")
    @PostMapping("/{orderNo}/confirm-receipt")
    public R<String> confirmReceipt(@PathVariable String orderNo) {
        requireOwnership(orderNo);
        shipmentService.confirmReceipt(orderNo);
        return R.ok("确认收货成功").setMessage("感谢您的确认，交易完成");
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
