package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigTagsRelationMapperByMySql;

/** config_tags_relation 表 Mapper。 */
public class ConfigTagsRelationMapperByDm extends ConfigTagsRelationMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
