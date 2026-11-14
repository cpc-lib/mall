package cc.ivera.order.infrastructure.persistence.mapper;

import cc.ivera.order.infrastructure.persistence.po.OrderRequestPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderRequestMapper extends BaseMapper<OrderRequestPO> {

    @Update("UPDATE t_order_request SET status = 'SUCCESS', order_no = #{orderNo}, response_body = #{responseBody}, "
        + "update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PROCESSING'")
    int markSuccess(@Param("id") Long id,
                    @Param("orderNo") String orderNo,
                    @Param("responseBody") String responseBody);

    @Update("UPDATE t_order_request SET status = 'FAILED', update_time = CURRENT_TIMESTAMP "
        + "WHERE id = #{id} AND status = 'PROCESSING'")
    int markFailed(@Param("id") Long id);

    @Update("UPDATE t_order_request SET status = 'PROCESSING', update_time = CURRENT_TIMESTAMP "
        + "WHERE id = #{id} AND status = 'FAILED' AND request_hash = #{requestHash}")
    int casFailedToProcessing(@Param("id") Long id, @Param("requestHash") String requestHash);
}
