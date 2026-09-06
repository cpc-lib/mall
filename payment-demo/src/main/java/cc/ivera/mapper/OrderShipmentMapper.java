package cc.ivera.mapper;

import cc.ivera.entity.OrderShipment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface OrderShipmentMapper extends BaseMapper<OrderShipment> {

    /**
     * 物流状态 CAS 迁移（按运单号）。
     * timeColumn：shippedTime/inTransitTime/deliveredTime/receivedTime。
     */
    int casShipmentStatus(@Param("trackingNo") String trackingNo,
                          @Param("from") String from,
                          @Param("to") String to,
                          @Param("timeColumn") String timeColumn);
}
