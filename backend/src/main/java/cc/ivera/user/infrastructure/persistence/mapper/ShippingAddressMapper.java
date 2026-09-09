package cc.ivera.user.infrastructure.persistence.mapper;

import cc.ivera.user.infrastructure.persistence.po.ShippingAddressPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShippingAddressMapper extends BaseMapper<ShippingAddressPO> {
}
