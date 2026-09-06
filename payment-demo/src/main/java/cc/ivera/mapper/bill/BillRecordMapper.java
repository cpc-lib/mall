package cc.ivera.mapper.bill;

import cc.ivera.entity.bill.BillRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单流水原始记录 Mapper。
 */
public interface BillRecordMapper extends BaseMapper<BillRecord> {

    /**
     * 批次明细列表：importId 必填，recordType 非空时按记录类型过滤，按 id 升序。
     */
    List<BillRecord> selectRecordsByImport(@Param("importId") Long importId,
                                           @Param("recordType") String recordType);
}
