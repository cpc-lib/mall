package cc.ivera.refund.infrastructure.persistence.mapper;

import cc.ivera.refund.infrastructure.persistence.po.RefundOrderPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrderPO> {

    Long countAllRefundOrders();

    List<RefundOrderPO> selectPageCreateTimeDesc(@Param("limit") int limit, @Param("offset") long offset);
}
