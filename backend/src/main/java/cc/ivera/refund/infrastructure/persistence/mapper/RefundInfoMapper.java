package cc.ivera.refund.infrastructure.persistence.mapper;

import cc.ivera.refund.infrastructure.persistence.po.RefundInfoPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface RefundInfoMapper extends BaseMapper<RefundInfoPO> {

    Integer sumRefundAmountByOrderNoAndStatuses(@Param("orderNo") String orderNo,
                                                @Param("statuses") Collection<String> statuses);

    Integer sumRefundAmountByOrderNoAndApprovalStatuses(@Param("orderNo") String orderNo,
                                                        @Param("approvalStatuses") Collection<String> approvalStatuses);
}
