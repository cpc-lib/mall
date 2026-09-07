package cc.ivera.mapper;

import cc.ivera.entity.RefundOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface RefundOrderMapper extends BaseMapper<RefundOrder> {
    RefundOrder selectByRefundNoForUpdate(@Param("refundNo") String refundNo);
}
