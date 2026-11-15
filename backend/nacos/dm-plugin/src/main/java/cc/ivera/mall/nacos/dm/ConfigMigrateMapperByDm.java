package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigMigrateMapperByMysql;

/** 配置迁移相关 Mapper。 */
public class ConfigMigrateMapperByDm extends ConfigMigrateMapperByMysql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
