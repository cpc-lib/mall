package cc.ivera.controller;

import cc.ivera.dto.auth.*;
import cc.ivera.security.AuthContext;
import cc.ivera.service.AuthService;
import cc.ivera.service.PasswordResetRequestService;
import cc.ivera.vo.LoginVO;
import cc.ivera.vo.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
@Validated
public class AuthController {
    private final AuthService authService;
    private final PasswordResetRequestService passwordResetRequestService;

    public AuthController(AuthService authService, PasswordResetRequestService passwordResetRequestService) {
        this.authService = authService;
        this.passwordResetRequestService = passwordResetRequestService;
    }

    @PostMapping("/register")
    public R<?> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.login(request, clientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public R<?> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(AuthContext.get(), request == null ? null : request.getRefreshToken());
        return R.ok().setMessage("已退出登录");
    }

    @PostMapping("/password")
    public R<?> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(AuthContext.get(), request);
        return R.ok().setMessage("密码修改成功，旧 Token 已失效");
    }

    @PostMapping("/admin/reset-password")
    public R<?> resetPassword(@Valid @RequestBody AdminResetPasswordRequest request) {
        authService.adminResetPassword(request);
        return R.ok().setMessage("密码重置成功，目标用户旧 Token 已失效");
    }

    @PostMapping("/password-reset-request")
    public R<?> submitPasswordResetRequest(@Valid @RequestBody PasswordResetRequestSubmitRequest request) {
        passwordResetRequestService.submit(request);
        return R.ok().setMessage("申请已提交，请联系管理员处理");
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 64) return first;
        }
        return request.getRemoteAddr();
    }
}
