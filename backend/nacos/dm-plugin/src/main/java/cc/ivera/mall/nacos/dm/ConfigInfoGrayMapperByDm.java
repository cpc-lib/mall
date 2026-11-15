package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigInfoGrayMapperByMySql;

/** config_info_gray 表 Mapper。 */
public class ConfigInfoGrayMapperByDm extends ConfigInfoGrayMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
