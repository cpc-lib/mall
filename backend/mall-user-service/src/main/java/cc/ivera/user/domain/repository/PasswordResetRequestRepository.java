package cc.ivera.user.domain.repository;

import cc.ivera.user.domain.model.PasswordResetRequest;

import java.util.List;

public interface PasswordResetRequestRepository {

    void save(PasswordResetRequest request);

    void update(PasswordResetRequest request);

    PasswordResetRequest findById(Long id);

    /**
     * 该用户名待处理（PENDING）申请数。
     */
    int countPendingByUsername(String username);

    List<PasswordResetRequest> listAll();
}
