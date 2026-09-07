package cc.ivera.service.impl;

import cc.ivera.entity.ShippingAddress;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.ShippingAddressMapper;
import cc.ivera.service.ShippingAddressService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class ShippingAddressServiceImpl implements ShippingAddressService {
    private final ShippingAddressMapper mapper;

    public ShippingAddressServiceImpl(ShippingAddressMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ShippingAddress> listByUser(Long userId) {
        QueryWrapper<ShippingAddress> q = new QueryWrapper<>();
        q.eq("user_id", userId).orderByDesc("is_default").orderByDesc("create_time");
        return mapper.selectList(q);
    }

    @Override
    public ShippingAddress getById(Long userId, Long id) {
        ShippingAddress a = mapper.selectById(id);
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
        mapper.insert(addr);
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
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        ShippingAddress a = getById(userId, id);
        boolean wasDefault = "1".equals(a.getIsDefault());
        mapper.deleteById(id);
        // 若删的是默认地址，自动将该用户最新一条非默认地址升为默认
        if (wasDefault) {
            QueryWrapper<ShippingAddress> q = new QueryWrapper<>();
            q.eq("user_id", userId).orderByDesc("create_time").last("limit 1");
            ShippingAddress next = mapper.selectOne(q);
            if (next != null) {
                next.setIsDefault("1");
                mapper.updateById(next);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        ShippingAddress target = getById(userId, id);
        setDefaultNoTx(userId, target);
        mapper.updateById(target);
    }

    @Override
    public ShippingAddress defaultOf(Long userId) {
        QueryWrapper<ShippingAddress> q = new QueryWrapper<>();
        q.eq("user_id", userId).eq("is_default", "1").last("limit 1");
        ShippingAddress d = mapper.selectOne(q);
        if (d != null) return d;
        q.clear(); q.eq("user_id", userId).orderByDesc("create_time").last("limit 1");
        return mapper.selectOne(q);
    }

    /** 同事务内：将该用户全部地址 is_default 置 0，再将 target 置 1。target 尚未 insert 也可（仅更新既有行）。 */
    private void setDefaultNoTx(Long userId, ShippingAddress target) {
        UpdateWrapper<ShippingAddress> clear = new UpdateWrapper<>();
        clear.eq("user_id", userId).set("is_default", "0");
        mapper.update(null, clear);
        target.setIsDefault("1");
    }
}
