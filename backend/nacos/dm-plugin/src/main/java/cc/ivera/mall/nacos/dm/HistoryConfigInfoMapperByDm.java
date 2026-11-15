package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.HistoryConfigInfoMapperByMySql;

/**
 * his_config_info 表 Mapper。
 * 删除历史记录使用 {@code DELETE ... WHERE gmt_modified < ? LIMIT ?}，DM8 支持该写法，无需改写。
 */
public class HistoryConfigInfoMapperByDm extends HistoryConfigInfoMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
