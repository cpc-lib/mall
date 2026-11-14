package cc.ivera.product.infrastructure.persistence.repository;

import cc.ivera.product.domain.model.InventoryReservation;
import cc.ivera.product.domain.repository.InventoryReservationRepository;
import cc.ivera.product.infrastructure.persistence.converter.InventoryReservationPOConverter;
import cc.ivera.product.infrastructure.persistence.mapper.InventoryReservationMapper;
import cc.ivera.product.infrastructure.persistence.po.InventoryReservationPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class InventoryReservationRepositoryImpl implements InventoryReservationRepository {

    private final InventoryReservationMapper reservationMapper;

    public InventoryReservationRepositoryImpl(InventoryReservationMapper reservationMapper) {
        this.reservationMapper = reservationMapper;
    }

    @Override
    public void insert(InventoryReservation reservation) {
        reservationMapper.insert(InventoryReservationPOConverter.toPO(reservation));
    }

    @Override
    public int casTransition(String orderNo, String from, String to, String timeColumn) {
        return reservationMapper.casTransition(orderNo, from, to, timeColumn);
    }

    @Override
    public List<InventoryReservation> listByOrderNo(String orderNo) {
        List<InventoryReservationPO> rows = reservationMapper.selectList(
            new LambdaQueryWrapper<InventoryReservationPO>().eq(InventoryReservationPO::getOrderNo, orderNo)
                .orderByAsc(InventoryReservationPO::getId));
        return rows.stream().map(InventoryReservationPOConverter::toDomain).collect(Collectors.toList());
    }
}
