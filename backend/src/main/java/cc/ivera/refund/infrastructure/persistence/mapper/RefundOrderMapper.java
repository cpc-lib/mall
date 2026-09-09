package cc.ivera.refund.infrastructure.persistence.mapper;

import cc.ivera.refund.infrastructure.persistence.po.RefundOrderPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrderPO> {
    RefundOrderPO selectByRefundNoForUpdate(@Param("refundNo") String refundNo);
}
