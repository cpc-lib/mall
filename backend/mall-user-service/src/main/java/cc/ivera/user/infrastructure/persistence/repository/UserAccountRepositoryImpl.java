package cc.ivera.user.infrastructure.persistence.repository;

import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.domain.repository.UserAccountRepository;
import cc.ivera.user.infrastructure.persistence.converter.UserAccountPOConverter;
import cc.ivera.user.infrastructure.persistence.mapper.UserAccountMapper;
import cc.ivera.user.infrastructure.persistence.po.UserAccountPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class UserAccountRepositoryImpl implements UserAccountRepository {

    private final UserAccountMapper userAccountMapper;

    public UserAccountRepositoryImpl(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public void save(UserAccount user) {
        UserAccountPO po = UserAccountPOConverter.toPO(user);
        userAccountMapper.insert(po);
        user.setId(po.getId());
        user.setCreateTime(po.getCreateTime());
        user.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(UserAccount user) {
        userAccountMapper.updateById(UserAccountPOConverter.toPO(user));
    }

    @Override
    public UserAccount findById(Long id) {
        return UserAccountPOConverter.toDomain(userAccountMapper.selectById(id));
    }

    @Override
    public UserAccount findByUsername(String username) {
        return UserAccountPOConverter.toDomain(userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountPO>()
            .eq(UserAccountPO::getUsername, username == null ? null : username.trim())));
    }

    @Override
    public long countAdminUsers(String keywordLike) {
        Long count = userAccountMapper.countAdminUsers(keywordLike);
        return count == null ? 0L : count;
    }

    @Override
    public List<UserAccount> listAdminUsers(String keywordLike, int limit, long offset) {
        return userAccountMapper.selectAdminUsers(keywordLike, limit, offset).stream()
            .map(UserAccountPOConverter::toDomain).collect(Collectors.toList());
    }
}
