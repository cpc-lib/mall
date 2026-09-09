package cc.ivera.order.infrastructure.persistence.repository;

import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.domain.repository.OrderShipmentRepository;
import cc.ivera.order.infrastructure.persistence.converter.OrderShipmentPOConverter;
import cc.ivera.order.infrastructure.persistence.mapper.OrderShipmentMapper;
import cc.ivera.order.infrastructure.persistence.po.OrderShipmentPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 物流单聚合仓储实现。
 */
@Repository
public class OrderShipmentRepositoryImpl implements OrderShipmentRepository {

    private final OrderShipmentMapper orderShipmentMapper;

    public OrderShipmentRepositoryImpl(OrderShipmentMapper orderShipmentMapper) {
        this.orderShipmentMapper = orderShipmentMapper;
    }

    @Override
    public void save(OrderShipment shipment) {
        OrderShipmentPO po = OrderShipmentPOConverter.toPO(shipment);
        orderShipmentMapper.insert(po);
        shipment.setId(po.getId());
        shipment.setCreateTime(po.getCreateTime());
        shipment.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public OrderShipment findLatestByOrderNo(String orderNo) {
        List<OrderShipmentPO> list = orderShipmentMapper.selectList(new LambdaQueryWrapper<OrderShipmentPO>()
            .eq(OrderShipmentPO::getOrderNo, orderNo)
            .orderByDesc(OrderShipmentPO::getId)
            .last("limit 1"));
        return list.isEmpty() ? null : OrderShipmentPOConverter.toDomain(list.get(0));
    }

    @Override
    public List<OrderShipment> listByOrderNos(List<String> orderNos) {
        return orderShipmentMapper.selectList(new LambdaQueryWrapper<OrderShipmentPO>()
                .in(OrderShipmentPO::getOrderNo, orderNos)
                .orderByDesc(OrderShipmentPO::getId))
            .stream().map(OrderShipmentPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderShipment> listShippedBefore(Date cutoff) {
        return orderShipmentMapper.selectList(new LambdaQueryWrapper<OrderShipmentPO>()
                .eq(OrderShipmentPO::getStatus, "SHIPPED")
                .lt(OrderShipmentPO::getShippedTime, cutoff))
            .stream().map(OrderShipmentPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<OrderShipment> listInTransitBefore(Date cutoff) {
        return orderShipmentMapper.selectList(new LambdaQueryWrapper<OrderShipmentPO>()
                .eq(OrderShipmentPO::getStatus, "IN_TRANSIT")
                .lt(OrderShipmentPO::getInTransitTime, cutoff))
            .stream().map(OrderShipmentPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public int casTransition(String trackingNo, String from, String to, String timeColumn) {
        return orderShipmentMapper.casShipmentStatus(trackingNo, from, to, timeColumn);
    }
}
