package cc.ivera.user.domain.gateway;

import cc.ivera.shared.security.AuthPrincipal;

/**
 * Access Token 签发与校验端口，由 infrastructure.security 适配器实现。
 */
public interface TokenPort {

    String issue(Long userId, String username, String role, long tokenVersion, String sid);

    AuthPrincipal parse(String token);

    long getAccessTtlSeconds();
}
