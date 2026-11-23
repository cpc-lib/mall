package cc.ivera.user.infrastructure.persistence.repository;

import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.domain.repository.PasswordResetRequestRepository;
import cc.ivera.user.infrastructure.persistence.converter.PasswordResetRequestPOConverter;
import cc.ivera.user.infrastructure.persistence.mapper.PasswordResetRequestMapper;
import cc.ivera.user.infrastructure.persistence.po.PasswordResetRequestPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
    public int markResolvedIfStatusIn(Long id, Collection<String> allowed, String newStatus,
                                      Long handledBy, String adminRemark) {
        LambdaUpdateWrapper<PasswordResetRequestPO> wrapper = new LambdaUpdateWrapper<PasswordResetRequestPO>()
            .eq(PasswordResetRequestPO::getId, id)
            .in(allowed != null && !allowed.isEmpty() ? PasswordResetRequestPO::getStatus : PasswordResetRequestPO::getId,
                allowed != null && !allowed.isEmpty() ? allowed : Collections.singleton("__never_match__"))
            .set(PasswordResetRequestPO::getStatus, newStatus)
            .set(PasswordResetRequestPO::getHandledBy, handledBy)
            .set(PasswordResetRequestPO::getAdminRemark, adminRemark)
            .set(PasswordResetRequestPO::getUpdateTime, new Date());
        return passwordResetRequestMapper.update(null, wrapper);
    }

    @Override
    public PasswordResetRequest findById(Long id) {
        return PasswordResetRequestPOConverter.toDomain(passwordResetRequestMapper.selectById(id));
    }

    @Override
    public int countPendingByUsername(String username) {
        Long count = passwordResetRequestMapper.selectCount(new LambdaQueryWrapper<PasswordResetRequestPO>()
            .eq(PasswordResetRequestPO::getUsername, username)
            .eq(PasswordResetRequestPO::getStatus, STATUS_PENDING));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public List<PasswordResetRequest> listPagePendingFirst(int limit, long offset) {
        return passwordResetRequestMapper.selectPagePendingFirst(limit, offset).stream()
            .map(PasswordResetRequestPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countAll() {
        Long total = passwordResetRequestMapper.countAllRequests();
        return total == null ? 0L : total;
    }
}
