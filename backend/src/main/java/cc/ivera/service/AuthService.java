package cc.ivera.service;

import cc.ivera.dto.auth.*;
import cc.ivera.entity.UserAccount;
import cc.ivera.security.AuthPrincipal;
import cc.ivera.vo.LoginVO;

public interface AuthService {
    UserAccount register(RegisterRequest request);
    LoginVO login(LoginRequest request, String clientIp);
    LoginVO refresh(String refreshToken);
    void logout(AuthPrincipal principal, String refreshToken);
    void changePassword(AuthPrincipal principal, PasswordChangeRequest request);
    void adminResetPassword(AdminResetPasswordRequest request);
    AuthPrincipal verifyAccessToken(String token);
}
