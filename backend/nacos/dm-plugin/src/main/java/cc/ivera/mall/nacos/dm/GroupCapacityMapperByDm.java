package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.GroupCapacityMapperByMysql;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * group_capacity 表 Mapper。
 * 与 tenant_capacity 相同，MySQL 实现中的反引号标识符在 DM8 下需去掉。
 */
public class GroupCapacityMapperByDm extends GroupCapacityMapperByMysql {

    @Override
    public String getDataSource() {
        return "dm";
    }

    @Override
    public MapperResult selectGroupInfoBySize(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.selectGroupInfoBySize(context));
    }

    @Override
    public MapperResult select(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.select(context));
    }

    @Override
    public MapperResult insertIntoSelect(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.insertIntoSelect(context));
    }

    @Override
    public MapperResult insertIntoSelectByWhere(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.insertIntoSelectByWhere(context));
    }

    @Override
    public MapperResult incrementUsageByWhereQuotaEqualZero(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.incrementUsageByWhereQuotaEqualZero(context));
    }

    @Override
    public MapperResult incrementUsageByWhereQuotaNotEqualZero(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.incrementUsageByWhereQuotaNotEqualZero(context));
    }

    @Override
    public MapperResult incrementUsageByWhere(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.incrementUsageByWhere(context));
    }

    @Override
    public MapperResult decrementUsageByWhere(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.decrementUsageByWhere(context));
    }

    @Override
    public MapperResult updateUsage(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.updateUsage(context));
    }

    @Override
    public MapperResult updateUsageByWhere(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.updateUsageByWhere(context));
    }
}
