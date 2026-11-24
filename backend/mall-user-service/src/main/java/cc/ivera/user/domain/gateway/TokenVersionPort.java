package cc.ivera.user.domain.gateway;

/**
 * Access Token 全局版本号端口：版本变化即让旧 Token 失效，由 infrastructure 适配器实现。
 */
public interface TokenVersionPort {

    long currentVersion(Long userId);

    void bumpVersion(Long userId);
}
