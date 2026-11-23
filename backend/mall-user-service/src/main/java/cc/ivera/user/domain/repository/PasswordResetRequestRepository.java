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

    /**
     * 管理员分页：待处理优先，同状态按申请时间倒序。
     */
    List<PasswordResetRequest> listPagePendingFirst(int limit, long offset);

    /**
     * 申请总数（管理员分页用）。
     */
    long countAll();
}
