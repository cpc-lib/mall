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

    /**
     * 导入批次列表：billDate 非空时按账单日期过滤，按 id 倒序。
     */
    List<BillImportPO> selectImportsByDate(@Param("billDate") String billDate);
}
