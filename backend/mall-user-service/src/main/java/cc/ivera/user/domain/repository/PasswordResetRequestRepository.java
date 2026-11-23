package cc.ivera.user.domain.repository;

import cc.ivera.user.domain.model.PasswordResetRequest;

import java.util.List;

public interface PasswordResetRequestRepository {

    void save(PasswordResetRequest request);

    @Deprecated
    void update(PasswordResetRequest request);

    /**
     * 条件更新：仅当当前状态在 allowed 集合内时，置为 newStatus 并写入 handledBy / adminRemark。
     * 用于 handle/reject 等锁外管理操作，避免并发时先读后改的脏对象覆盖。
     *
     * @return 实际更新行数；0 表示已被其他管理员处理，跳过
     */
    int markResolvedIfStatusIn(Long id, java.util.Collection<String> allowed, String newStatus,
                               Long handledBy, String adminRemark);

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
