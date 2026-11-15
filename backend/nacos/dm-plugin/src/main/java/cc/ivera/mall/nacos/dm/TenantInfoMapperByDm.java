package cc.ivera.mall.nacos.dm;

import com.alibaba.nacos.plugin.datasource.impl.mysql.TenantInfoMapperByMySql;

/** tenant_info 表 Mapper。 */
public class TenantInfoMapperByDm extends TenantInfoMapperByMySql {

    @Override
    public String getDataSource() {
        return "dm";
    }
}
