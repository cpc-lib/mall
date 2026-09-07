package cc.ivera.service.impl;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderShipment;
import cc.ivera.enums.FulfillmentStatus;
import cc.ivera.enums.PayStatus;
import cc.ivera.enums.ShipmentStatus;
import cc.ivera.exception.BizException;
import cc.ivera.lock.DistributedLockTemplate;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.mapper.OrderShipmentMapper;
import cc.ivera.service.InventoryService;
import cc.ivera.service.ShipmentService;
import cc.ivera.service.logistics.LogisticsProvider;
import cc.ivera.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

/**
 * 履约/物流域服务实现（V2）。
 * 发货与确认收货均为 CAS 条件更新，保证并发与幂等；
 * 模拟推进由定时任务驱动，任何时刻重复执行不产生副作用。
 */
@Service
@Slf4j
public class ShipmentServiceImpl implements ShipmentService {

    /** 发货后多少毫秒进入"运输中"（模拟）。 */
    @Value("${logistics.mock.in-transit-delay-ms:20000}")
    private long inTransitDelayMs;

    /** 进入运输后多少毫秒"派送送达"（模拟）。 */
    @Value("${logistics.mock.delivered-delay-ms:40000}")
    private long deliveredDelayMs;

    private final OrderInfoMapper orderInfoMapper;

    private final OrderShipmentMapper orderShipmentMapper;

    private final LogisticsProvider logisticsProvider;

    private final DistributedLockTemplate distributedLockTemplate;

    private final TransactionTemplate transactionTemplate;

    private final InventoryService inventoryService;

    public ShipmentServiceImpl(OrderInfoMapper orderInfoMapper,
                               OrderShipmentMapper orderShipmentMapper,
                               LogisticsProvider logisticsProvider,
                               DistributedLockTemplate distributedLockTemplate,
                               TransactionTemplate transactionTemplate,
                               InventoryService inventoryService) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderShipmentMapper = orderShipmentMapper;
        this.logisticsProvider = logisticsProvider;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
        this.inventoryService = inventoryService;
    }

    @Override
    public OrderShipment ship(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BizException("订单号不能为空");
        }
        return distributedLockTemplate.execute("shipment:ship:" + orderNo, 5000L, -1L, () ->
                transactionTemplate.execute(status -> doShip(orderNo))
        );
    }

    private OrderShipment doShip(String orderNo) {
        OrderInfo order = orderInfoMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException("订单不存在，orderNo=" + orderNo);
        }
        if (!PayStatus.PAID.getType().equals(order.getPayStatus())) {
            throw new BizException("订单未支付，不能发货，orderNo=" + orderNo);
        }
        if (order.getRefundStatus() != null && !"NONE".equals(order.getRefundStatus())) {
            throw new BizException("订单已退款或退款中，不可发货，refundStatus=" + order.getRefundStatus());
        }
        if (FulfillmentStatus.RECEIVED.getType().equals(order.getFulfillmentStatus())) {
            throw new BizException("订单已确认收货，orderNo=" + orderNo);
        }
        if (!FulfillmentStatus.WAIT_SHIP.getType().equals(order.getFulfillmentStatus())) {
            throw new BizException("订单当前履约状态不可发货：" + order.getFulfillmentStatus());
        }
        if (findShipment(orderNo) != null) {
            throw new BizException("订单已存在物流单，不能重复发货，orderNo=" + orderNo);
        }

        // 订单履约状态 CAS：WAIT_SHIP → SHIPPED。
        int cas = orderInfoMapper.update(null, new UpdateWrapper<OrderInfo>()
                .eq("order_no", orderNo)
                .eq("fulfillment_status", FulfillmentStatus.WAIT_SHIP.getType())
                .set("fulfillment_status", FulfillmentStatus.SHIPPED.getType()));
        if (cas == 0) {
            throw new BizException("发货失败：订单履约状态已被并发变更，orderNo=" + orderNo);
        }

        // 对接物流商创建运单。
        String trackingNo = logisticsProvider.createWaybill(order);
        OrderShipment shipment = new OrderShipment();
        shipment.setShipmentNo(OrderNoUtils.getShipmentNo());
        shipment.setOrderNo(orderNo);
        shipment.setLogisticsCompany("模拟快递");
        shipment.setTrackingNo(trackingNo);
        shipment.setStatus(ShipmentStatus.SHIPPED.getType());
        shipment.setShippedTime(new Date());
        shipment.setRemark("模拟物流商发货");
        orderShipmentMapper.insert(shipment);
        log.info("订单发货成功，orderNo={}, trackingNo={}", orderNo, trackingNo);
        return shipment;
    }

    @Override
    public void confirmReceipt(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BizException("订单号不能为空");
        }
        distributedLockTemplate.execute("shipment:receipt:" + orderNo, 5000L, -1L, () ->
                transactionTemplate.execute(status -> {
                    doConfirmReceipt(orderNo);
                    return null;
                })
        );
    }

    private void doConfirmReceipt(String orderNo) {
        OrderInfo order = orderInfoMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException("订单不存在，orderNo=" + orderNo);
        }
        if (order.getRefundStatus() != null && !"NONE".equals(order.getRefundStatus())) {
            throw new BizException("订单已退款或退款中，不可确认收货，refundStatus=" + order.getRefundStatus());
        }
        OrderShipment shipment = findShipment(orderNo);
        if (shipment == null) {
            throw new BizException("订单尚未发货，orderNo=" + orderNo);
        }
        if (ShipmentStatus.RECEIVED.getType().equals(shipment.getStatus())) {
            log.info("订单已确认收货，幂等返回，orderNo={}", orderNo);
            return;
        }
        // 已发货即可确认收货，不强制等待物流送达（模拟环境中用户主动确认即视为签收）
        if (!ShipmentStatus.SHIPPED.getType().equals(shipment.getStatus())
                && !ShipmentStatus.IN_TRANSIT.getType().equals(shipment.getStatus())
                && !ShipmentStatus.DELIVERED.getType().equals(shipment.getStatus())) {
            throw new BizException("物流状态不允许确认收货，orderNo=" + orderNo);
        }

        // 物流单 CAS：SHIPPED/IN_TRANSIT/DELIVERED → RECEIVED（用户主动确认即视为签收）。
        int cas = orderShipmentMapper.update(null, new UpdateWrapper<OrderShipment>()
                .eq("tracking_no", shipment.getTrackingNo())
                .in("status", ShipmentStatus.SHIPPED.getType(),
                        ShipmentStatus.IN_TRANSIT.getType(),
                        ShipmentStatus.DELIVERED.getType())
                .set("status", ShipmentStatus.RECEIVED.getType())
                .set("received_time", new Date()));
        if (cas == 0) {
            throw new BizException("确认收货失败：物流状态已被并发变更，orderNo=" + orderNo);
        }

        // 订单履约状态 CAS：SHIPPED → RECEIVED。
        orderInfoMapper.update(null, new UpdateWrapper<OrderInfo>()
                .eq("order_no", orderNo)
                .eq("fulfillment_status", FulfillmentStatus.SHIPPED.getType())
                .set("fulfillment_status", FulfillmentStatus.RECEIVED.getType()));

        // 锁定库存结转已售（同事务；流水 bizNo 幂等，重复收货在上方已拦截）。
        inventoryService.convertToSoldOnReceipt(orderNo);
        log.info("订单确认收货完成，orderNo={}", orderNo);
    }

    @Override
    public OrderShipment getShipment(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return null;
        }
        return findShipment(orderNo);
    }

    @Override
    public void advanceMockLogistics() {
        Date now = new Date();
        // SHIPPED → IN_TRANSIT：以发货时间为基准。
        // 注意：queryTimeColumn 直接拼进 WHERE，必须用下划线 DB 列名；
        // targetTimeColumn 是 casShipmentStatus XML <choose> 的逻辑键，用驼峰。
        advanceTransition(ShipmentStatus.SHIPPED.getType(), ShipmentStatus.IN_TRANSIT.getType(),
                "shipped_time", "inTransitTime", new Date(now.getTime() - inTransitDelayMs));
        // IN_TRANSIT → DELIVERED：以进入运输时间为基准。
        advanceTransition(ShipmentStatus.IN_TRANSIT.getType(), ShipmentStatus.DELIVERED.getType(),
                "in_transit_time", "deliveredTime", new Date(now.getTime() - deliveredDelayMs));
    }

    private void advanceTransition(String from, String to, String queryTimeColumn, String targetTimeColumn, Date cutoff) {
        List<OrderShipment> shipments = orderShipmentMapper.selectList(new QueryWrapper<OrderShipment>()
                .eq("status", from)
                .lt(queryTimeColumn, cutoff));
        for (OrderShipment shipment : shipments) {
            int cas = orderShipmentMapper.casShipmentStatus(shipment.getTrackingNo(), from, to, targetTimeColumn);
            if (cas > 0) {
                log.info("模拟物流推进，trackingNo={}, {} -> {}", shipment.getTrackingNo(), from, to);
            }
        }
    }

    private OrderShipment findShipment(String orderNo) {
        List<OrderShipment> list = orderShipmentMapper.selectList(new QueryWrapper<OrderShipment>()
                .eq("order_no", orderNo)
                .orderByDesc("id")
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}
