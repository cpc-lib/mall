package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * DM8 与 MySQL 的 Mapper SQL 差异集中在两处：
 * <ul>
 *   <li>DM8 不支持反引号标识符，`` `usage` `` 需改写为 {@code usage}；</li>
 *   <li>其余语法（LIMIT ?,?、LIMIT n、子查询、INSERT ... SELECT）均与 DM8 兼容。</li>
 * </ul>
 */
final class DmSqlSupport {

    private DmSqlSupport() {
    }

    /** 去掉 MySQL 反引号，DM8 下按普通标识符解析。 */
    static MapperResult stripBackticks(MapperResult result) {
        return new MapperResult(result.getSql().replace("`", ""), result.getParamList());
    }
}
