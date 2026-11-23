package cc.ivera.bill.infrastructure.persistence.mapper;

import cc.ivera.bill.infrastructure.persistence.po.BillImportPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单导入批次 Mapper。
 */
@Mapper
public interface BillImportMapper extends BaseMapper<BillImportPO> {

    Long countImportsByDate(@Param("billDate") String billDate);

    List<BillImportPO> selectPageByDate(@Param("billDate") String billDate,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);
}
