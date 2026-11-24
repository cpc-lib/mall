package cc.ivera.user.domain.gateway;

/**
 * 登录防爆破端口：按用户名做失败计数与锁定，由 infrastructure.security 适配器实现。
 */
public interface LoginGuardPort {

    void checkLocked(String username);

    void recordFailure(String username);

    void clear(String username);

    void clearUsername(String username);
}
