package cc.ivera.user.infrastructure.persistence.repository;

import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.domain.repository.PasswordResetRequestRepository;
import cc.ivera.user.infrastructure.persistence.converter.PasswordResetRequestPOConverter;
import cc.ivera.user.infrastructure.persistence.mapper.PasswordResetRequestMapper;
import cc.ivera.user.infrastructure.persistence.po.PasswordResetRequestPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class PasswordResetRequestRepositoryImpl implements PasswordResetRequestRepository {

    private static final String STATUS_PENDING = "PENDING";

    private final PasswordResetRequestMapper passwordResetRequestMapper;

    public PasswordResetRequestRepositoryImpl(PasswordResetRequestMapper passwordResetRequestMapper) {
        this.passwordResetRequestMapper = passwordResetRequestMapper;
    }

    @Override
    public void save(PasswordResetRequest request) {
        PasswordResetRequestPO po = PasswordResetRequestPOConverter.toPO(request);
        passwordResetRequestMapper.insert(po);
        request.setId(po.getId());
        request.setCreateTime(po.getCreateTime());
        request.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(PasswordResetRequest request) {
        passwordResetRequestMapper.updateById(PasswordResetRequestPOConverter.toPO(request));
    }

    @Override
    public PasswordResetRequest findById(Long id) {
        return PasswordResetRequestPOConverter.toDomain(passwordResetRequestMapper.selectById(id));
    }

    @Override
    public int countPendingByUsername(String username) {
        Integer count = passwordResetRequestMapper.selectCount(new LambdaQueryWrapper<PasswordResetRequestPO>()
            .eq(PasswordResetRequestPO::getUsername, username)
            .eq(PasswordResetRequestPO::getStatus, STATUS_PENDING));
        return count == null ? 0 : count;
    }

    @Override
    public List<PasswordResetRequest> listAll() {
        return passwordResetRequestMapper.selectList(null).stream()
            .map(PasswordResetRequestPOConverter::toDomain).collect(Collectors.toList());
    }
}
