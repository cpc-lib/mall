package cc.ivera.mapper.bill;

import cc.ivera.entity.bill.BillReconcileDiscrepancy;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单对账差异单 Mapper。
 */
public interface BillReconcileDiscrepancyMapper extends BaseMapper<BillReconcileDiscrepancy> {

    /**
     * 批次差异列表：importId 必填，bizType/discrepancyType/status 非空时分别过滤，按 id 升序。
     */
    List<BillReconcileDiscrepancy> selectDiscrepanciesByImport(@Param("importId") Long importId,
                                                               @Param("bizType") String bizType,
                                                               @Param("discrepancyType") String discrepancyType,
                                                               @Param("status") String status);
}
