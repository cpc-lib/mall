package cc.ivera.user.application;

import cc.ivera.shared.security.AccessTokenVerifier;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.application.dto.AdminResetPasswordRequest;
import cc.ivera.user.application.dto.LoginRequest;
import cc.ivera.user.application.dto.PasswordChangeRequest;
import cc.ivera.user.application.dto.RegisterRequest;
import cc.ivera.user.application.vo.LoginVO;

public interface AuthService extends AccessTokenVerifier {
    UserAccount register(RegisterRequest request);

    LoginVO login(LoginRequest request, String ip, String userAgent);

    LoginVO refresh(String refreshToken);

    void logout(AuthPrincipal principal, String refreshToken);

    void changePassword(AuthPrincipal principal, PasswordChangeRequest request);

    void adminResetPassword(AdminResetPasswordRequest request);

}
