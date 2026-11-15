package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigInfoBetaMapperByMySql;

/** config_info_beta 表 Mapper。 */
public class ConfigInfoBetaMapperByDm extends ConfigInfoBetaMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
