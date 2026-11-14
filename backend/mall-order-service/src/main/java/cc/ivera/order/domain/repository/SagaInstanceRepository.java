package cc.ivera.order.domain.repository;

import cc.ivera.order.domain.model.SagaInstance;

import java.util.Date;
import java.util.List;

/**
 * 下单 Saga 仓储端口：状态推进一律使用 CAS 条件更新。
 */
public interface SagaInstanceRepository {

    void save(SagaInstance saga);

    SagaInstance findBySagaNo(String sagaNo);

    SagaInstance findByBiz(String bizType, String bizNo);

    int markSucceeded(String sagaNo);

    int markFailed(String sagaNo, String currentStep);

    int scheduleRetry(String sagaNo, int retryCount, Date nextRetryTime);

    /**
     * 扫描卡在 PROCESSING 的 Saga：创建时间早于 cutoff，且未安排重试或已到重试时间。
     */
    List<SagaInstance> listRecoverable(Date cutoff, int limit);
}
