package cc.ivera.order.infrastructure.persistence.repository;

import cc.ivera.order.domain.model.SagaInstance;
import cc.ivera.order.domain.repository.SagaInstanceRepository;
import cc.ivera.order.infrastructure.persistence.converter.SagaInstancePOConverter;
import cc.ivera.order.infrastructure.persistence.mapper.SagaInstanceMapper;
import cc.ivera.order.infrastructure.persistence.po.SagaInstancePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 下单 Saga 仓储实现。
 */
@Repository
public class SagaInstanceRepositoryImpl implements SagaInstanceRepository {

    private final SagaInstanceMapper sagaInstanceMapper;

    public SagaInstanceRepositoryImpl(SagaInstanceMapper sagaInstanceMapper) {
        this.sagaInstanceMapper = sagaInstanceMapper;
    }

    @Override
    public void save(SagaInstance saga) {
        SagaInstancePO po = SagaInstancePOConverter.toPO(saga);
        sagaInstanceMapper.insert(po);
        saga.setId(po.getId());
        saga.setCreateTime(po.getCreateTime());
        saga.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public SagaInstance findBySagaNo(String sagaNo) {
        return SagaInstancePOConverter.toDomain(sagaInstanceMapper.selectOne(new LambdaQueryWrapper<SagaInstancePO>()
            .eq(SagaInstancePO::getSagaNo, sagaNo)
            .last("limit 1")));
    }

    @Override
    public SagaInstance findByBiz(String bizType, String bizNo) {
        return SagaInstancePOConverter.toDomain(sagaInstanceMapper.selectOne(new LambdaQueryWrapper<SagaInstancePO>()
            .eq(SagaInstancePO::getBizType, bizType)
            .eq(SagaInstancePO::getBizNo, bizNo)
            .last("limit 1")));
    }

    @Override
    public int markSucceeded(String sagaNo) {
        return sagaInstanceMapper.markSucceeded(sagaNo);
    }

    @Override
    public int markFailed(String sagaNo, String currentStep) {
        return sagaInstanceMapper.markFailed(sagaNo, currentStep);
    }

    @Override
    public int scheduleRetry(String sagaNo, int retryCount, Date nextRetryTime) {
        return sagaInstanceMapper.scheduleRetry(sagaNo, retryCount, nextRetryTime);
    }

    @Override
    public List<SagaInstance> listRecoverable(Date cutoff, int limit) {
        return sagaInstanceMapper.selectList(new LambdaQueryWrapper<SagaInstancePO>()
                .eq(SagaInstancePO::getStatus, "PROCESSING")
                .le(SagaInstancePO::getCreateTime, cutoff)
                .and(w -> w.isNull(SagaInstancePO::getNextRetryTime)
                    .or().le(SagaInstancePO::getNextRetryTime, new Date()))
                .orderByAsc(SagaInstancePO::getId)
                .last("limit " + limit))
            .stream().map(SagaInstancePOConverter::toDomain).collect(Collectors.toList());
    }
}
