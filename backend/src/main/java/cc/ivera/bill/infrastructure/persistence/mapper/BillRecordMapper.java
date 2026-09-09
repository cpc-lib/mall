package cc.ivera.bill.infrastructure.persistence.mapper;

import cc.ivera.bill.infrastructure.persistence.po.BillRecordPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单流水原始记录 Mapper。
 */
@Mapper
public interface BillRecordMapper extends BaseMapper<BillRecordPO> {

    /**
     * 批次明细列表：importId 必填，recordType 非空时按记录类型过滤，按 id 升序。
     */
    List<BillRecordPO> selectRecordsByImport(@Param("importId") Long importId,
                                             @Param("recordType") String recordType);
}
