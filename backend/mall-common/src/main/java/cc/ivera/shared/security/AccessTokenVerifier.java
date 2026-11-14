package cc.ivera.shared.security;

public interface AccessTokenVerifier {
    AuthPrincipal verifyAccessToken(String token);
}
