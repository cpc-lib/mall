package cc.ivera.controller;

import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderShipment;
import cc.ivera.entity.PaymentOrder;
import cc.ivera.enums.FulfillmentStatus;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayStatus;
import cc.ivera.enums.PayType;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.mapper.PaymentOrderMapper;
import cc.ivera.service.AliPayService;
import cc.ivera.service.CheckoutService;
import cc.ivera.service.OrderInfoService;
import cc.ivera.service.ShipmentService;
import cc.ivera.service.wxpay.WxPayOrderFacade;
import cc.ivera.util.JsonUtils;
import cc.ivera.vo.ChannelOrderQueryVO;
import cc.ivera.vo.OrderDetailVO;
import cc.ivera.vo.R;
import cc.ivera.vo.WxPayStatusVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/**
 * 管理员订单履约（V2）：待发货订单列表 + 模拟物流发货 + 全部订单管理 + 异常处理。
 */
@RestController
@RequestMapping("/api/admin/order")
@CrossOrigin
@Validated
@Api(tags = "管理员订单履约API")
public class AdminOrderShipmentController {

    private final ShipmentService shipmentService;
    private final OrderInfoMapper orderInfoMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final CheckoutService checkoutService;
    private final OrderInfoService orderInfoService;
    private final WxPayOrderFacade wxPayOrderFacade;
    private final AliPayService aliPayService;

    public AdminOrderShipmentController(ShipmentService shipmentService,
                                       OrderInfoMapper orderInfoMapper,
                                       PaymentOrderMapper paymentOrderMapper,
                                       CheckoutService checkoutService,
                                       OrderInfoService orderInfoService,
                                       WxPayOrderFacade wxPayOrderFacade,
                                       AliPayService aliPayService) {
        this.shipmentService = shipmentService;
        this.orderInfoMapper = orderInfoMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.checkoutService = checkoutService;
        this.orderInfoService = orderInfoService;
        this.wxPayOrderFacade = wxPayOrderFacade;
        this.aliPayService = aliPayService;
    }

    @ApiOperation("待发货订单列表（已支付未发货）")
    @GetMapping("/wait-ship")
    public R<List<OrderInfo>> waitShipList() {
        return R.ok(orderInfoMapper.selectList(new QueryWrapper<OrderInfo>()
                .eq("pay_status", PayStatus.PAID.getType())
                .eq("fulfillment_status", FulfillmentStatus.WAIT_SHIP.getType())
                .orderByAsc("paid_time")));
    }

    @ApiOperation("全部订单列表（管理员，可选状态/订单号/用户编号/下单时间范围筛选）")
    @GetMapping("/all")
    public R<List<OrderDetailVO>> allOrders(
            @RequestParam(value = "payStatus", required = false) String payStatus,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "fulfillmentStatus", required = false) String fulfillmentStatus,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        return R.ok(checkoutService.listAllOrders(payStatus, orderStatus, fulfillmentStatus,
                orderNo, userId, startTime, endTime));
    }

    @ApiOperation("强制关单（仅未支付订单，释放预占库存）")
    @PostMapping("/{orderNo}/force-close")
    public R<?> forceClose(@PathVariable String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) throw new cc.ivera.exception.BizException("订单不存在");
        if (!PayStatus.UNPAID.getType().equals(order.getPayStatus()))
            throw new cc.ivera.exception.BizException("仅未支付订单可强制关单，当前支付状态：" + order.getPayStatus());
        // 幂等：已被超时调度器或前次操作关闭的订单直接返回成功
        if (OrderStatus.CLOSED.getType().equals(order.getOrderStatus())) {
            return R.ok().setMessage("订单已关闭（幂等），无需重复操作");
        }
        boolean ok = orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CLOSED);
        if (!ok) throw new cc.ivera.exception.BizException("关单失败：订单状态可能已被并发修改，请刷新后重试");
        return R.ok().setMessage("订单已强制关闭，预占库存已释放");
    }

    @ApiOperation("手动标记支付成功（仅未支付订单，提交预占库存）")
    @PostMapping("/{orderNo}/mark-paid")
    public R<?> markPaid(@PathVariable String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) throw new cc.ivera.exception.BizException("订单不存在");
        if (!PayStatus.UNPAID.getType().equals(order.getPayStatus()))
            throw new cc.ivera.exception.BizException("仅未支付订单可标记支付，当前支付状态：" + order.getPayStatus());
        boolean ok = orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.SUCCESS);
        if (!ok) throw new cc.ivera.exception.BizException("标记失败：订单状态可能已被并发修改，请刷新后重试");
        return R.ok().setMessage("订单已标记为支付成功，预占库存已提交");
    }

    @ApiOperation("模拟发货（对接模拟物流商创建运单）")
    @PostMapping("/{orderNo}/ship")
    public R<OrderShipment> ship(@PathVariable String orderNo) {
        OrderShipment shipment = shipmentService.ship(orderNo);
        return R.ok(shipment).setMessage("发货成功，运单号 " + shipment.getTrackingNo());
    }

    @ApiOperation("支付尝试记录（本订单全部渠道支付单：渠道/状态/渠道交易号/金额/时间）")
    @GetMapping("/{orderNo}/payment-orders")
    public R<List<PaymentOrder>> paymentOrders(
            @PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return R.ok(paymentOrderMapper.selectList(new QueryWrapper<PaymentOrder>()
                .eq("order_no", orderNo)
                .orderByAsc("id")));
    }

    @ApiOperation("渠道订单查询（主动向微信/支付宝查单，已支付成功则同步本地订单；不自动关单）")
    @GetMapping("/{orderNo}/channel-query")
    public R<ChannelOrderQueryVO> channelQuery(
            @PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }

        ChannelOrderQueryVO vo;
        if (PayType.WXPAY.getType().equals(order.getPaymentType())) {
            WxPayStatusVO wx = wxPayOrderFacade.queryPaymentStatus(orderNo);
            vo = new ChannelOrderQueryVO();
            vo.setOrderNo(orderNo);
            vo.setChannelCode(PaymentConfigLoader.CHANNEL_WXPAY);
            vo.setChannelTradeState(wx.getTradeState());
            vo.setChannelTradeStateDesc(wx.getTradeStateDesc());
            vo.setLocalOrderStatusBefore(wx.getLocalStatusBefore());
            vo.setLocalOrderStatusAfter(wx.getLocalStatus());
            OrderInfo after = orderInfoService.getOrderByOrderNo(orderNo);
            vo.setLocalPayStatusAfter(after == null ? null : after.getPayStatus());
            vo.setSynced(!Objects.equals(wx.getLocalStatusBefore(), wx.getLocalStatus()));
            vo.setChannelRawBody(wx.getWxPayResult() == null ? null : JsonUtils.toJson(wx.getWxPayResult()));
        } else if (PayType.ALIPAY.getType().equals(order.getPaymentType())) {
            vo = aliPayService.queryAndSyncStatus(orderNo);
        } else {
            throw new BizException("该订单无支付渠道，无法查询渠道订单");
        }
        return R.ok(vo);
    }
}
