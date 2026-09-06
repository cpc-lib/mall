package cc.ivera.mapper;

import cc.ivera.entity.RefundInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

public interface RefundInfoMapper extends BaseMapper<RefundInfo> {

    /**
     * 按商户退款单号查询并加行级排他锁。
     * 用于退款通知、退款状态同步、退款审核等并发场景。
     */
    RefundInfo selectByRefundNoForUpdate(@Param("refundNo") String refundNo);

    Integer sumRefundAmountByOrderNoAndStatuses(@Param("orderNo") String orderNo,
                                                @Param("statuses") Collection<String> statuses);

    Integer sumRefundAmountByOrderNoAndApprovalStatuses(@Param("orderNo") String orderNo,
                                                        @Param("approvalStatuses") Collection<String> approvalStatuses);
}
