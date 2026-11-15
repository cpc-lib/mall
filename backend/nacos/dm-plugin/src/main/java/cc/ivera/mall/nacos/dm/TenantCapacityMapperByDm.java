package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.TenantCapacityMapperByMySql;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * tenant_capacity 表 Mapper。
 * MySQL 实现用反引号包裹 dm 关键字 {@code usage}，DM8 不支持反引号，需统一去掉。
 */
public class TenantCapacityMapperByDm extends TenantCapacityMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }

    @Override
    public MapperResult select(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.select(context));
    }

    @Override
    public MapperResult getCapacityList4CorrectUsage(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.getCapacityList4CorrectUsage(context));
    }

    @Override
    public MapperResult incrementUsageWithDefaultQuotaLimit(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.incrementUsageWithDefaultQuotaLimit(context));
    }

    @Override
    public MapperResult incrementUsageWithQuotaLimit(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.incrementUsageWithQuotaLimit(context));
    }

    @Override
    public MapperResult incrementUsage(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.incrementUsage(context));
    }

    @Override
    public MapperResult decrementUsage(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.decrementUsage(context));
    }

    @Override
    public MapperResult correctUsage(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.correctUsage(context));
    }

    @Override
    public MapperResult insertTenantCapacity(MapperContext context) {
        return DmSqlSupport.stripBackticks(super.insertTenantCapacity(context));
    }
}
