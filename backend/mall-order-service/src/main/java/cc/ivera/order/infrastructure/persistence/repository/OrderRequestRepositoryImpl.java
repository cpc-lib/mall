package cc.ivera.order.infrastructure.persistence.repository;

import cc.ivera.order.domain.model.OrderRequest;
import cc.ivera.order.domain.repository.OrderRequestRepository;
import cc.ivera.order.infrastructure.persistence.converter.OrderRequestPOConverter;
import cc.ivera.order.infrastructure.persistence.mapper.OrderRequestMapper;
import cc.ivera.order.infrastructure.persistence.po.OrderRequestPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 下单请求幂等仓储实现。
 */
@Repository
public class OrderRequestRepositoryImpl implements OrderRequestRepository {

    private final OrderRequestMapper orderRequestMapper;

    public OrderRequestRepositoryImpl(OrderRequestMapper orderRequestMapper) {
        this.orderRequestMapper = orderRequestMapper;
    }

    @Override
    public OrderRequest find(String idemKey, Long userId, String api) {
        return OrderRequestPOConverter.toDomain(orderRequestMapper.selectOne(new LambdaQueryWrapper<OrderRequestPO>()
            .eq(OrderRequestPO::getIdemKey, idemKey)
            .eq(OrderRequestPO::getUserId, userId)
            .eq(OrderRequestPO::getApi, api)
            .last("limit 1")));
    }

    @Override
    public void insert(OrderRequest request) {
        OrderRequestPO po = OrderRequestPOConverter.toPO(request);
        orderRequestMapper.insert(po);
        request.setId(po.getId());
        request.setCreateTime(po.getCreateTime());
        request.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public int markSuccess(Long id, String orderNo, String responseBody) {
        return orderRequestMapper.markSuccess(id, orderNo, responseBody);
    }

    @Override
    public int markFailed(Long id) {
        return orderRequestMapper.markFailed(id);
    }

    @Override
    public int casFailedToProcessing(Long id, String requestHash) {
        return orderRequestMapper.casFailedToProcessing(id, requestHash);
    }

    @Override
    public int deleteExpired(Date now, int limit) {
        List<OrderRequestPO> expired = orderRequestMapper.selectList(new LambdaQueryWrapper<OrderRequestPO>()
            .select(OrderRequestPO::getId)
            .le(OrderRequestPO::getExpireAt, now)
            .orderByAsc(OrderRequestPO::getId)
            .last("limit " + limit));
        if (expired.isEmpty()) return 0;
        List<Long> ids = expired.stream().map(OrderRequestPO::getId).toList();
        return orderRequestMapper.deleteBatchIds(ids);
    }
}
