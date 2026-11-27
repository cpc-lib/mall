package cc.ivera.order.application.impl;

import cc.ivera.order.application.SagaHistoryCleanupService;
import cc.ivera.order.domain.repository.SagaHistoryRepository;
import cc.ivera.shared.domain.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 单批清理在一个本地事务内完成：查询候选 → 删明细 → 条件删实例 → 校验数量。
 * 若查询后实例状态被推进（终态 CAS 删除行数 < 选中数），抛异常回滚整批，等下一轮重试。
 */
@Service
public class SagaHistoryCleanupServiceImpl implements SagaHistoryCleanupService {

    private static final int BATCH_SIZE = 500;

    private final SagaHistoryRepository sagaHistoryRepository;

    public SagaHistoryCleanupServiceImpl(SagaHistoryRepository sagaHistoryRepository) {
        this.sagaHistoryRepository = sagaHistoryRepository;
    }

    @Override
    @Transactional
    public int cleanBatch(Date cutoff) {
        List<String> ids = sagaHistoryRepository.findCleanableInstanceIds(cutoff, BATCH_SIZE);
        if (ids.isEmpty()) {
            return 0;
        }
        sagaHistoryRepository.deleteStateInstancesByMachineInstIds(ids);
        int deleted = sagaHistoryRepository.deleteMachineInstancesTerminalBefore(cutoff, ids);
        if (deleted != ids.size()) {
            throw new BizException("Seata Saga 历史实例清理终态校验失败：选中 " + ids.size()
                + " 条，实例实际删除 " + deleted + " 条，查询后状态已变更，整批回滚等待下一轮");
        }
        return deleted;
    }
}
