package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigInfoTagMapperByMySql;

/** config_info_tag 表 Mapper。 */
public class ConfigInfoTagMapperByDm extends ConfigInfoTagMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
