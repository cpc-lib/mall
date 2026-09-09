package cc.ivera.refund.infrastructure.persistence.mapper;

import cc.ivera.refund.infrastructure.persistence.po.RefundInfoPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface RefundInfoMapper extends BaseMapper<RefundInfoPO> {

    /**
     * 按商户退款单号查询并加行级排他锁。
     * 用于退款通知、退款状态同步、退款审核等并发场景。
     */
    RefundInfoPO selectByRefundNoForUpdate(@Param("refundNo") String refundNo);

    Integer sumRefundAmountByOrderNoAndStatuses(@Param("orderNo") String orderNo,
                                                @Param("statuses") Collection<String> statuses);

    Integer sumRefundAmountByOrderNoAndApprovalStatuses(@Param("orderNo") String orderNo,
                                                        @Param("approvalStatuses") Collection<String> approvalStatuses);
}
