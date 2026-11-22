package cc.ivera.order.infrastructure.persistence.mapper;

import cc.ivera.order.infrastructure.persistence.po.OrderShipmentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface OrderShipmentMapper extends BaseMapper<OrderShipmentPO> {

    List<OrderShipmentPO> selectAutoReceiptCandidates(@Param("cutoff") Date cutoff, @Param("limit") int limit);

    /**
     * 物流状态 CAS 迁移（按运单号）。
     * timeColumn：shippedTime/inTransitTime/deliveredTime/receivedTime。
     */
    int casShipmentStatus(@Param("trackingNo") String trackingNo,
                          @Param("from") String from,
                          @Param("to") String to,
                          @Param("timeColumn") String timeColumn,
                          @Param("now") Date now);
}
