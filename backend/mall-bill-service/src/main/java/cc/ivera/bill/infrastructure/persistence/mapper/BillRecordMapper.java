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

    /**
     * 差异下钻定位渠道流水：biz_no 或 channel_serial_no 任一相等，空白条件不参与匹配，按 id 升序。
     */
    List<BillRecordPO> selectRecordsByImportAndMatch(@Param("importId") Long importId,
                                                     @Param("recordType") String recordType,
                                                     @Param("bizNo") String bizNo,
                                                     @Param("channelSerialNo") String channelSerialNo);

    Long countRecordsByImport(@Param("importId") Long importId,
                              @Param("recordType") String recordType);

    List<BillRecordPO> selectPageByImport(@Param("importId") Long importId,
                                         @Param("recordType") String recordType,
                                         @Param("limit") int limit,
                                         @Param("offset") long offset);
}
