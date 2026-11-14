package cc.ivera.user.domain.repository;

import cc.ivera.user.domain.model.UserAccount;

import java.util.List;

public interface UserAccountRepository {

    void save(UserAccount user);

    void update(UserAccount user);

    UserAccount findById(Long id);

    UserAccount findByUsername(String username);

    /**
     * 管理员用户列表总数：username 关键字模糊（keywordLike 为 null 时查全量）。
     */
    long countAdminUsers(String keywordLike);

    /**
     * 管理员用户列表分页：username 关键字模糊可选，按 id 升序，LIMIT/OFFSET 手动分页。
     */
    List<UserAccount> listAdminUsers(String keywordLike, int limit, long offset);
}
