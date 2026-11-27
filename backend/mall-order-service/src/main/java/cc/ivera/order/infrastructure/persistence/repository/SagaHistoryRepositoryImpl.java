package cc.ivera.order.infrastructure.persistence.repository;

import cc.ivera.order.domain.repository.SagaHistoryRepository;
import cc.ivera.order.infrastructure.persistence.mapper.SagaHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public class SagaHistoryRepositoryImpl implements SagaHistoryRepository {

    private final SagaHistoryMapper sagaHistoryMapper;

    public SagaHistoryRepositoryImpl(SagaHistoryMapper sagaHistoryMapper) {
        this.sagaHistoryMapper = sagaHistoryMapper;
    }

    @Override
    public List<String> findCleanableInstanceIds(Date cutoff, int limit) {
        return sagaHistoryMapper.findCleanableInstanceIds(cutoff, limit);
    }

    @Override
    public int deleteStateInstancesByMachineInstIds(List<String> ids) {
        return sagaHistoryMapper.deleteStateInstancesByMachineInstIds(ids);
    }

    @Override
    public int deleteMachineInstancesTerminalBefore(Date cutoff, List<String> ids) {
        return sagaHistoryMapper.deleteMachineInstancesTerminalBefore(cutoff, ids);
    }
}
