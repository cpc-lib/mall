package cc.ivera.product.infrastructure.persistence.mapper;

import cc.ivera.product.infrastructure.persistence.po.StockImportPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockImportMapper extends BaseMapper<StockImportPO> {
}
