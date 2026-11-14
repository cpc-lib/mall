package cc.ivera.order.domain.model;

import cc.ivera.order.domain.enums.SagaStatus;
import lombok.Data;

import java.util.Date;

/**
 * 下单 Saga 实例（t_saga_instance）：持久化编排状态，支持崩溃恢复与补偿审计。
 */
@Data
public class SagaInstance {

    private Long id;

    private String sagaNo;

    private String bizType;

    private String bizNo;

    private String status;

    private String currentStep;

    private String payload;

    private Integer retryCount;

    private Date nextRetryTime;

    private Date createTime;

    private Date updateTime;

    public static SagaInstance start(String sagaNo, String bizType, String bizNo, String payload) {
        SagaInstance saga = new SagaInstance();
        saga.setSagaNo(sagaNo);
        saga.setBizType(bizType);
        saga.setBizNo(bizNo);
        saga.setStatus(SagaStatus.PROCESSING.getType());
        saga.setCurrentStep("CREATE_ORDER");
        saga.setPayload(payload);
        saga.setRetryCount(0);
        return saga;
    }
}
