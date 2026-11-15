package cc.ivera.order.application.impl;

import cc.ivera.order.application.ShipmentService;
import cc.ivera.order.application.vo.ShipmentDetailVO;
import cc.ivera.order.application.vo.ShipmentTimelineVO;
import cc.ivera.order.domain.enums.FulfillmentStatus;
import cc.ivera.order.domain.enums.ShipmentStatus;
import cc.ivera.order.domain.gateway.LogisticsGateway;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.order.domain.repository.OrderShipmentRepository;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.domain.model.OrderItemStockLine;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLock;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
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

    private final OrderRepository orderRepository;
    private final OrderShipmentRepository orderShipmentRepository;
    private final LogisticsGateway logisticsGateway;
    private final TransactionTemplate transactionTemplate;
    private final InventoryService inventoryService;
    /**
     * 发货后多少毫秒进入"运输中"（模拟）。
     */
    @Value("${logistics.mock.in-transit-delay-ms:20000}")
    private long inTransitDelayMs;
    /**
     * 进入运输后多少毫秒"派送送达"（模拟）。
     */
    @Value("${logistics.mock.delivered-delay-ms:40000}")
    private long deliveredDelayMs;

    public ShipmentServiceImpl(OrderRepository orderRepository,
                               OrderShipmentRepository orderShipmentRepository,
                               LogisticsGateway logisticsGateway,
                               TransactionTemplate transactionTemplate,
                               InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.orderShipmentRepository = orderShipmentRepository;
        this.logisticsGateway = logisticsGateway;
        this.transactionTemplate = transactionTemplate;
        this.inventoryService = inventoryService;
    }

    @Override
    @DistributedLock(key = "'shipment:ship:' + #orderNo", waitTimeMillis = 5000L)
    public OrderShipment ship(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BizException("订单号不能为空");
        }
        return transactionTemplate.execute(status -> doShip(orderNo));
    }

    private OrderShipment doShip(String orderNo) {
        OrderInfo order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在，orderNo=" + orderNo);
        }
        OrderShipment existing = orderShipmentRepository.findLatestByOrderNo(orderNo);
        // 发货前置守卫：已支付、无退款、未收货、待发货，且不存在已创建物流单。
        order.requireShippable(existing != null);

        // 订单履约状态 CAS：WAIT_SHIP → SHIPPED。
        int cas = orderRepository.casFulfillment(orderNo,
            FulfillmentStatus.WAIT_SHIP.getType(), FulfillmentStatus.SHIPPED.getType());
        if (cas == 0) {
            throw new BizException("发货失败：订单履约状态已被并发变更，orderNo=" + orderNo);
        }

        // 对接物流商创建运单。
        String trackingNo = logisticsGateway.createWaybill(order);
        OrderShipment shipment = OrderShipment.createShipped(
            OrderNoUtils.getShipmentNo(), orderNo, trackingNo, new Date());
        orderShipmentRepository.save(shipment);
        log.info("订单发货成功，orderNo={}, trackingNo={}", orderNo, trackingNo);
        return shipment;
    }

    @Override
    @DistributedLock(key = "'shipment:receipt:' + #orderNo", waitTimeMillis = 5000L)
    public void confirmReceipt(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BizException("订单号不能为空");
        }
        transactionTemplate.execute(status -> {
            doConfirmReceipt(orderNo);
            return null;
        });
    }

    private void doConfirmReceipt(String orderNo) {
        OrderInfo order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在，orderNo=" + orderNo);
        }
        // 退款守卫：存在退款（汇总状态非 NONE）不允许确认收货。
        order.requireOpenForReceipt();
        OrderShipment shipment = orderShipmentRepository.findLatestByOrderNo(orderNo);
        if (shipment == null) {
            throw new BizException("订单尚未发货，orderNo=" + orderNo);
        }
        if (shipment.isReceived()) {
            log.info("订单已确认收货，幂等返回，orderNo={}", orderNo);
            return;
        }
        // 仅物流送达（DELIVERED）后允许确认收货；已发货/运输中不可提前签收。
        shipment.requireDeliveredForReceipt(orderNo);

        // 物流单 CAS：DELIVERED → RECEIVED（时间列随 CAS 落库）。
        int cas = orderShipmentRepository.casTransition(shipment.getTrackingNo(),
            ShipmentStatus.DELIVERED.getType(), ShipmentStatus.RECEIVED.getType(), "receivedTime");
        if (cas == 0) {
            throw new BizException("确认收货失败：物流状态已被并发变更，orderNo=" + orderNo);
        }

        // 订单履约状态 CAS：SHIPPED → RECEIVED。
        int fulfillmentCas = orderRepository.casFulfillment(orderNo,
            FulfillmentStatus.SHIPPED.getType(), FulfillmentStatus.RECEIVED.getType());
        if (fulfillmentCas == 0) {
            // 不能带着「物流已收货、库存已结转」却不更新订单履约状态的中间态提交事务
            throw new BizException("确认收货失败：订单履约状态已被并发变更，orderNo=" + orderNo);
        }

        // 锁定库存结转已售（订单明细快照随请求传入；流水 bizNo 幂等，重复收货在上方已拦截）。
        List<OrderItem> orderItems = orderRepository.listItemsByOrderNo(orderNo);
        List<OrderItemStockLine> items = orderItems.stream()
            .map(i -> new OrderItemStockLine(i.getId(), i.getProductId(), i.getQuantity(), i.getRestockedQty()))
            .toList();
        inventoryService.convertToSoldOnReceipt(orderNo, items);
        log.info("订单确认收货完成，orderNo={}", orderNo);
    }

    @Override
    public OrderShipment getShipment(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return null;
        }
        return orderShipmentRepository.findLatestByOrderNo(orderNo);
    }

    @Override
    public ShipmentDetailVO getShipmentDetail(String orderNo) {
        ShipmentDetailVO data = new ShipmentDetailVO();
        data.setOrderNo(orderNo);
        OrderShipment shipment = getShipment(orderNo);
        if (shipment == null) {
            data.setShipped(false);
            return data;
        }
        data.setShipped(true);
        data.setShipmentNo(shipment.getShipmentNo());
        data.setLogisticsCompany(shipment.getLogisticsCompany());
        data.setTrackingNo(shipment.getTrackingNo());
        data.setStatus(shipment.getStatus());
        ShipmentTimelineVO timeline = new ShipmentTimelineVO();
        timeline.setSHIPPED(shipment.getShippedTime());
        timeline.setIN_TRANSIT(shipment.getInTransitTime());
        timeline.setDELIVERED(shipment.getDeliveredTime());
        timeline.setRECEIVED(shipment.getReceivedTime());
        data.setTimeline(timeline);
        return data;
    }

    @Override
    public void advanceMockLogistics() {
        Date now = new Date();
        // SHIPPED → IN_TRANSIT：以发货时间为基准。
        advanceTransition(ShipmentStatus.SHIPPED.getType(), ShipmentStatus.IN_TRANSIT.getType(),
            "inTransitTime", orderShipmentRepository.listShippedBefore(new Date(now.getTime() - inTransitDelayMs)));
        // IN_TRANSIT → DELIVERED：以进入运输时间为基准。
        advanceTransition(ShipmentStatus.IN_TRANSIT.getType(), ShipmentStatus.DELIVERED.getType(),
            "deliveredTime", orderShipmentRepository.listInTransitBefore(new Date(now.getTime() - deliveredDelayMs)));
    }

    private void advanceTransition(String from, String to, String targetTimeColumn, List<OrderShipment> shipments) {
        for (OrderShipment shipment : shipments) {
            int cas = orderShipmentRepository.casTransition(shipment.getTrackingNo(), from, to, targetTimeColumn);
            if (cas > 0) {
                log.info("模拟物流推进，trackingNo={}, {} -> {}", shipment.getTrackingNo(), from, to);
            }
        }
    }
}
