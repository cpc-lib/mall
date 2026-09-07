package cc.ivera.service;

import cc.ivera.entity.ShippingAddress;

import java.util.List;

public interface ShippingAddressService {
    List<ShippingAddress> listByUser(Long userId);
    ShippingAddress getById(Long userId, Long id);
    ShippingAddress create(Long userId, ShippingAddress addr);
    ShippingAddress update(Long userId, ShippingAddress addr);
    void delete(Long userId, Long id);
    /** 设为默认地址：同事务将该用户其他地址 is_default 置 '0' */
    void setDefault(Long userId, Long id);
    /** 取用户默认地址，若无默认则取第一条 */
    ShippingAddress defaultOf(Long userId);
}
