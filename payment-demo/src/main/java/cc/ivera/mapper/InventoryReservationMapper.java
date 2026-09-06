package cc.ivera.mapper;

import cc.ivera.entity.InventoryReservation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface InventoryReservationMapper extends BaseMapper<InventoryReservation> {

    /**
     * 预占状态 CAS 迁移（整单维度）。
     * timeColumn：commitTime → 写 commit_time；releaseTime → 写 release_time。
     */
    int casTransition(@Param("orderNo") String orderNo,
                      @Param("from") String from,
                      @Param("to") String to,
                      @Param("timeColumn") String timeColumn);
}
