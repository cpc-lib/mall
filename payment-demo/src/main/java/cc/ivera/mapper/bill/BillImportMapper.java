package cc.ivera.mapper.bill;

import cc.ivera.entity.bill.BillImport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单导入批次 Mapper。
 */
public interface BillImportMapper extends BaseMapper<BillImport> {

    /**
     * 导入批次列表：billDate 非空时按账单日期过滤，按 id 倒序。
     */
    List<BillImport> selectImportsByDate(@Param("billDate") String billDate);
}
