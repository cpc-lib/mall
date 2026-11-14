package cc.ivera.order.infrastructure.persistence.mapper;

import cc.ivera.order.infrastructure.persistence.po.SagaInstancePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

@Mapper
public interface SagaInstanceMapper extends BaseMapper<SagaInstancePO> {

    @Update("UPDATE t_saga_instance SET status = 'SUCCEEDED', update_time = CURRENT_TIMESTAMP "
        + "WHERE saga_no = #{sagaNo} AND status = 'PROCESSING'")
    int markSucceeded(@Param("sagaNo") String sagaNo);

    @Update("UPDATE t_saga_instance SET status = 'FAILED', current_step = #{currentStep}, update_time = CURRENT_TIMESTAMP "
        + "WHERE saga_no = #{sagaNo} AND status = 'PROCESSING'")
    int markFailed(@Param("sagaNo") String sagaNo, @Param("currentStep") String currentStep);

    @Update("UPDATE t_saga_instance SET retry_count = #{retryCount}, next_retry_time = #{nextRetryTime}, "
        + "update_time = CURRENT_TIMESTAMP WHERE saga_no = #{sagaNo} AND status = 'PROCESSING'")
    int scheduleRetry(@Param("sagaNo") String sagaNo,
                      @Param("retryCount") int retryCount,
                      @Param("nextRetryTime") Date nextRetryTime);
}
