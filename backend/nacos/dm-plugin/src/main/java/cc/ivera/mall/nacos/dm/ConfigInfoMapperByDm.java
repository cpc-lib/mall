package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigInfoMapperByMySql;

/** config_info 表 Mapper：SQL 沿用 MySQL 实现，仅数据源标识改为 dm。 */
public class ConfigInfoMapperByDm extends ConfigInfoMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
