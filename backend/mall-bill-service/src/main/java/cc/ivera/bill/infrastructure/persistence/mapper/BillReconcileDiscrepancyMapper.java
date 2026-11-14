package cc.ivera.bill.infrastructure.persistence.mapper;

import cc.ivera.bill.infrastructure.persistence.po.BillReconcileDiscrepancyPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单对账差异单 Mapper。
 */
@Mapper
public interface BillReconcileDiscrepancyMapper extends BaseMapper<BillReconcileDiscrepancyPO> {

    /**
     * 批次差异列表：importId 必填，bizType/discrepancyType/status 非空时分别过滤，按 id 升序。
     */
    List<BillReconcileDiscrepancyPO> selectDiscrepanciesByImport(@Param("importId") Long importId,
                                                                 @Param("bizType") String bizType,
                                                                 @Param("discrepancyType") String discrepancyType,
                                                                 @Param("status") String status);
}
