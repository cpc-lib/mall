package cc.ivera.order.application.saga;

import cc.ivera.order.domain.model.SagaInstance;
import cc.ivera.order.domain.repository.SagaInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 下单 Saga 状态写入：使用独立短事务，确保编排状态不被下单主事务回滚吞掉。
 */
@Service
public class OrderSagaService {

    public static final String BIZ_TYPE_CHECKOUT = "ORDER_CHECKOUT";
    public static final String STEP_CREATE_ORDER = "CREATE_ORDER";
    public static final String STEP_RESERVE_INVENTORY = "RESERVE_INVENTORY";

    private final SagaInstanceRepository sagaInstanceRepository;

    public OrderSagaService(SagaInstanceRepository sagaInstanceRepository) {
        this.sagaInstanceRepository = sagaInstanceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public SagaInstance start(String orderNo, String payload) {
        SagaInstance saga = SagaInstance.start("SAGA-" + orderNo, BIZ_TYPE_CHECKOUT, orderNo, payload);
        sagaInstanceRepository.save(saga);
        return saga;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void succeed(String sagaNo) {
        sagaInstanceRepository.markSucceeded(sagaNo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void fail(String sagaNo, String currentStep) {
        sagaInstanceRepository.markFailed(sagaNo, currentStep);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void scheduleRetry(String sagaNo, int retryCount, Date nextRetryTime) {
        sagaInstanceRepository.scheduleRetry(sagaNo, retryCount, nextRetryTime);
    }
}
