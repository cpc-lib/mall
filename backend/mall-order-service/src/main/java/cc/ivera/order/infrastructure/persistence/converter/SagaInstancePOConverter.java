package cc.ivera.order.infrastructure.persistence.converter;

import cc.ivera.order.domain.model.SagaInstance;
import cc.ivera.order.infrastructure.persistence.po.SagaInstancePO;

/**
 * 下单 Saga PO 与领域模型转换。
 */
public final class SagaInstancePOConverter {

    private SagaInstancePOConverter() {
    }

    public static SagaInstance toDomain(SagaInstancePO po) {
        if (po == null) return null;
        SagaInstance saga = new SagaInstance();
        saga.setId(po.getId());
        saga.setSagaNo(po.getSagaNo());
        saga.setBizType(po.getBizType());
        saga.setBizNo(po.getBizNo());
        saga.setStatus(po.getStatus());
        saga.setCurrentStep(po.getCurrentStep());
        saga.setPayload(po.getPayload());
        saga.setRetryCount(po.getRetryCount());
        saga.setNextRetryTime(po.getNextRetryTime());
        saga.setCreateTime(po.getCreateTime());
        saga.setUpdateTime(po.getUpdateTime());
        return saga;
    }

    public static SagaInstancePO toPO(SagaInstance saga) {
        SagaInstancePO po = new SagaInstancePO();
        po.setId(saga.getId());
        po.setSagaNo(saga.getSagaNo());
        po.setBizType(saga.getBizType());
        po.setBizNo(saga.getBizNo());
        po.setStatus(saga.getStatus());
        po.setCurrentStep(saga.getCurrentStep());
        po.setPayload(saga.getPayload());
        po.setRetryCount(saga.getRetryCount());
        po.setNextRetryTime(saga.getNextRetryTime());
        return po;
    }
}
