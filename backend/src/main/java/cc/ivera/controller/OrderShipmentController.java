package cc.ivera.controller;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderShipment;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.security.AuthContext;
import cc.ivera.service.ShipmentService;
import cc.ivera.service.RefundOrderService;
import cc.ivera.vo.RefundApplyVO;
import cc.ivera.vo.R;
import cc.ivera.vo.ShipmentDetailVO;
import cc.ivera.vo.ShipmentTimelineVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    private final OrderInfoMapper orderInfoMapper;

    public OrderShipmentController(ShipmentService shipmentService, RefundOrderService refundOrderService, OrderInfoMapper orderInfoMapper) {
        this.shipmentService = shipmentService;
        this.refundOrderService = refundOrderService;
        this.orderInfoMapper = orderInfoMapper;
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

    /** 校验订单存在且属于当前登录用户。 */
    private void requireOwnership(String orderNo) {
        Long userId = AuthContext.userId();
        OrderInfo order = orderInfoMapper.selectOne(new QueryWrapper<OrderInfo>()
                .eq("order_no", orderNo).eq("user_id", userId));
        if (order == null) {
            throw new BizException("订单不存在或无权访问");
        }
    }
}
