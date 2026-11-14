package cc.ivera.user.domain.repository;

import cc.ivera.user.domain.model.ShippingAddress;

import java.util.List;

public interface ShippingAddressRepository {

    /**
     * 用户全部地址：默认优先，再按创建时间倒序。
     */
    List<ShippingAddress> listByUser(Long userId);

    ShippingAddress findById(Long id);

    void save(ShippingAddress address);

    void update(ShippingAddress address);

    void deleteById(Long id);

    /**
     * 取用户默认地址（is_default='1'）。
     */
    ShippingAddress findDefaultByUser(Long userId);

    /**
     * 取用户最新一条地址（删除默认地址后的升补依据）。
     */
    ShippingAddress findLatestByUser(Long userId);

    /**
     * 将该用户全部地址 is_default 置 '0'。
     */
    void clearDefaultByUser(Long userId);
}
