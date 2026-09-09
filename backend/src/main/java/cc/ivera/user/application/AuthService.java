package cc.ivera.user.application;

import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.interfaces.dto.AdminResetPasswordRequest;
import cc.ivera.user.interfaces.dto.LoginRequest;
import cc.ivera.user.interfaces.dto.PasswordChangeRequest;
import cc.ivera.user.interfaces.dto.RegisterRequest;
import cc.ivera.user.interfaces.vo.LoginVO;

public interface AuthService {
    UserAccount register(RegisterRequest request);

    LoginVO login(LoginRequest request, String clientIp);

    LoginVO refresh(String refreshToken);

    void logout(AuthPrincipal principal, String refreshToken);

    void changePassword(AuthPrincipal principal, PasswordChangeRequest request);

    void adminResetPassword(AdminResetPasswordRequest request);

    AuthPrincipal verifyAccessToken(String token);
}
