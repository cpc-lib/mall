package cc.ivera.user.infrastructure.persistence.repository;

import cc.ivera.user.domain.model.ShippingAddress;
import cc.ivera.user.domain.repository.ShippingAddressRepository;
import cc.ivera.user.infrastructure.persistence.converter.ShippingAddressPOConverter;
import cc.ivera.user.infrastructure.persistence.mapper.ShippingAddressMapper;
import cc.ivera.user.infrastructure.persistence.po.ShippingAddressPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ShippingAddressRepositoryImpl implements ShippingAddressRepository {

    private final ShippingAddressMapper shippingAddressMapper;

    public ShippingAddressRepositoryImpl(ShippingAddressMapper shippingAddressMapper) {
        this.shippingAddressMapper = shippingAddressMapper;
    }

    @Override
    public List<ShippingAddress> listByUser(Long userId) {
        return shippingAddressMapper.selectList(new LambdaQueryWrapper<ShippingAddressPO>()
                .eq(ShippingAddressPO::getUserId, userId)
                .orderByDesc(ShippingAddressPO::getIsDefault)
                .orderByDesc(ShippingAddressPO::getCreateTime))
            .stream().map(ShippingAddressPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public ShippingAddress findById(Long id) {
        return ShippingAddressPOConverter.toDomain(shippingAddressMapper.selectById(id));
    }

    @Override
    public void save(ShippingAddress address) {
        ShippingAddressPO po = ShippingAddressPOConverter.toPO(address);
        shippingAddressMapper.insert(po);
        address.setId(po.getId());
        address.setCreateTime(po.getCreateTime());
        address.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(ShippingAddress address) {
        shippingAddressMapper.updateById(ShippingAddressPOConverter.toPO(address));
    }

    @Override
    public void deleteById(Long id) {
        shippingAddressMapper.deleteById(id);
    }

    @Override
    public ShippingAddress findDefaultByUser(Long userId) {
        return ShippingAddressPOConverter.toDomain(shippingAddressMapper.selectOne(new LambdaQueryWrapper<ShippingAddressPO>()
            .eq(ShippingAddressPO::getUserId, userId)
            .eq(ShippingAddressPO::getIsDefault, "1")
            .last("limit 1")));
    }

    @Override
    public ShippingAddress findLatestByUser(Long userId) {
        return ShippingAddressPOConverter.toDomain(shippingAddressMapper.selectOne(new LambdaQueryWrapper<ShippingAddressPO>()
            .eq(ShippingAddressPO::getUserId, userId)
            .orderByDesc(ShippingAddressPO::getCreateTime)
            .last("limit 1")));
    }

    @Override
    public void clearDefaultByUser(Long userId) {
        shippingAddressMapper.update(null, new LambdaUpdateWrapper<ShippingAddressPO>()
            .eq(ShippingAddressPO::getUserId, userId)
            .set(ShippingAddressPO::getIsDefault, "0"));
    }
}
