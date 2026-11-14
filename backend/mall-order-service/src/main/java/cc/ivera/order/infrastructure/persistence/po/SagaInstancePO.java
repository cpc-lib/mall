package cc.ivera.order.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 下单 Saga 实例 PO：t_saga_instance。
 */
@Data
@TableName("t_saga_instance")
public class SagaInstancePO extends BaseEntity {

    private String sagaNo;

    private String bizType;

    private String bizNo;

    private String status;

    private String currentStep;

    private String payload;

    private Integer retryCount;

    private Date nextRetryTime;
}
