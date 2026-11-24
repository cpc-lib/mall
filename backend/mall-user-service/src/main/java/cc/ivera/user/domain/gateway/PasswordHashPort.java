package cc.ivera.user.domain.gateway;

/**
 * 密码摘要端口，由 infrastructure.security 适配器实现。
 */
public interface PasswordHashPort {

    String newSalt();

    String hash(String password, String salt);

    boolean matches(String password, String salt, String expected);
}
