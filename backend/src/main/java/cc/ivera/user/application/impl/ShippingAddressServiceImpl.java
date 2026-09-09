package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.user.application.ShippingAddressService;
import cc.ivera.user.domain.model.ShippingAddress;
import cc.ivera.user.domain.repository.ShippingAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class ShippingAddressServiceImpl implements ShippingAddressService {
    private final ShippingAddressRepository repository;

    public ShippingAddressServiceImpl(ShippingAddressRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ShippingAddress> listByUser(Long userId) {
        return repository.listByUser(userId);
    }

    @Override
    public ShippingAddress getById(Long userId, Long id) {
        ShippingAddress a = repository.findById(id);
        if (a == null || !a.getUserId().equals(userId)) throw new BizException("地址不存在");
        return a;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShippingAddress create(Long userId, ShippingAddress addr) {
        addr.setId(null);
        addr.setUserId(userId);
        addr.setCreateTime(new Date());
        addr.setUpdateTime(new Date());
        if ("1".equals(addr.getIsDefault())) setDefaultNoTx(userId, addr);
        else if (listByUser(userId).isEmpty()) addr.setIsDefault("1"); // 首条自动为默认
        repository.save(addr);
        return addr;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShippingAddress update(Long userId, ShippingAddress addr) {
        ShippingAddress existing = getById(userId, addr.getId());
        existing.setReceiverName(addr.getReceiverName());
        existing.setReceiverPhone(addr.getReceiverPhone());
        existing.setProvince(addr.getProvince());
        existing.setCity(addr.getCity());
        existing.setDistrict(addr.getDistrict());
        existing.setDetail(addr.getDetail());
        existing.setUpdateTime(new Date());
        if ("1".equals(addr.getIsDefault())) setDefaultNoTx(userId, existing);
        repository.update(existing);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        ShippingAddress a = getById(userId, id);
        boolean wasDefault = "1".equals(a.getIsDefault());
        repository.deleteById(id);
        // 若删的是默认地址，自动将该用户最新一条非默认地址升为默认
        if (wasDefault) {
            ShippingAddress next = repository.findLatestByUser(userId);
            if (next != null) {
                next.setIsDefault("1");
                repository.update(next);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        ShippingAddress target = getById(userId, id);
        setDefaultNoTx(userId, target);
        repository.update(target);
    }

    @Override
    public ShippingAddress defaultOf(Long userId) {
        ShippingAddress d = repository.findDefaultByUser(userId);
        if (d != null) return d;
        return repository.findLatestByUser(userId);
    }

    /**
     * 同事务内：将该用户全部地址 is_default 置 0，再将 target 置 1。target 尚未 insert 也可（仅更新既有行）。
     */
    private void setDefaultNoTx(Long userId, ShippingAddress target) {
        repository.clearDefaultByUser(userId);
        target.setIsDefault("1");
    }
}
