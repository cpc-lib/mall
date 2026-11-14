package cc.ivera.user.interfaces;

import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.AuthService;
import cc.ivera.user.application.AuthSessionService;
import cc.ivera.user.application.PasswordResetRequestService;
import cc.ivera.user.application.RegionService;
import cc.ivera.user.application.ShippingAddressService;
import cc.ivera.user.application.UserAdminService;
import cc.ivera.user.application.dto.*;
import cc.ivera.user.application.vo.LoginVO;
import cc.ivera.user.application.vo.PasswordResetHandleVO;
import cc.ivera.user.application.vo.UserVO;
import cc.ivera.user.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/user")
public class UserInternalController {
    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final PasswordResetRequestService passwordResetRequestService;
    private final ShippingAddressService shippingAddressService;
    private final RegionService regionService;
    private final UserAdminService userAdminService;

    public UserInternalController(AuthService authService, AuthSessionService authSessionService,
                                  PasswordResetRequestService passwordResetRequestService,
                                  ShippingAddressService shippingAddressService, RegionService regionService,
                                  UserAdminService userAdminService) {
        this.authService = authService;
        this.authSessionService = authSessionService;
        this.passwordResetRequestService = passwordResetRequestService;
        this.shippingAddressService = shippingAddressService;
        this.regionService = regionService;
        this.userAdminService = userAdminService;
    }

    @PostMapping("/auth/register")
    public R<UserAccount> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @PostMapping("/auth/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request,
                            @RequestParam(required = false) String ip,
                            @RequestParam(required = false) String userAgent) {
        return R.ok(authService.login(request, ip, userAgent));
    }

    @PostMapping("/auth/refresh")
    public R<LoginVO> refresh(@RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refresh(request == null ? null : request.getRefreshToken()));
    }

    @PostMapping("/auth/logout")
    public R<?> logout(@RequestParam(required = false) Long userId,
                       @RequestParam(required = false) String sid,
                       @RequestBody(required = false) RefreshTokenRequest request) {
        AuthPrincipal principal = userId == null ? null : new AuthPrincipal(userId, null, null, 0, sid);
        authService.logout(principal, request == null ? null : request.getRefreshToken());
        return R.ok();
    }

    @PostMapping("/auth/password")
    public R<?> changePassword(@RequestParam Long userId, @Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(new AuthPrincipal(userId, null, null, 0, null), request);
        return R.ok();
    }

    @PostMapping("/auth/admin/reset-password")
    public R<?> adminResetPassword(@Valid @RequestBody AdminResetPasswordRequest request) {
        authService.adminResetPassword(request);
        return R.ok();
    }

    @PostMapping("/auth/verify-access-token")
    public R<AuthPrincipal> verifyAccessToken(@RequestBody AccessTokenRequest request) {
        return R.ok(authService.verifyAccessToken(request == null ? null : request.getAccessToken()));
    }

    @GetMapping("/sessions/{userId}")
    public R<List<AuthSession>> sessions(@PathVariable Long userId) {
        return R.ok(authSessionService.listSessionsByUser(userId));
    }

    @DeleteMapping("/sessions/{userId}/{sid}")
    public R<?> kick(@PathVariable Long userId, @PathVariable String sid) {
        authSessionService.kick(userId, sid);
        return R.ok();
    }

    @DeleteMapping("/sessions/{userId}")
    public R<?> kickAll(@PathVariable Long userId) {
        authSessionService.kickAll(userId);
        return R.ok();
    }

    @PostMapping("/password-reset-requests")
    public R<?> submitPasswordReset(@Valid @RequestBody PasswordResetRequestSubmitRequest request) {
        passwordResetRequestService.submit(request);
        return R.ok();
    }

    @GetMapping("/password-reset-requests")
    public R<List<PasswordResetRequest>> passwordResetRequests() {
        return R.ok(passwordResetRequestService.list());
    }

    @PostMapping("/password-reset-requests/{id}/handle")
    public R<PasswordResetHandleVO> handlePasswordReset(@PathVariable Long id,
                                                        @RequestParam Long operatorUserId,
                                                        @RequestBody(required = false) PasswordResetHandleRequest request) {
        return R.ok(passwordResetRequestService.handle(id, request, operatorUserId));
    }

    @PostMapping("/password-reset-requests/{id}/reject")
    public R<?> rejectPasswordReset(@PathVariable Long id,
                                    @RequestParam Long operatorUserId,
                                    @RequestBody(required = false) PasswordResetRejectRequest request) {
        passwordResetRequestService.reject(id, request, operatorUserId);
        return R.ok();
    }

    @GetMapping("/addresses")
    public R<List<ShippingAddress>> addresses(@RequestParam Long userId) {
        return R.ok(shippingAddressService.listByUser(userId));
    }

    @GetMapping("/addresses/{id}")
    public R<ShippingAddress> address(@PathVariable Long id, @RequestParam Long userId) {
        return R.ok(shippingAddressService.getById(userId, id));
    }

    @PostMapping("/addresses")
    public R<ShippingAddress> createAddress(@RequestParam Long userId, @RequestBody ShippingAddress address) {
        return R.ok(shippingAddressService.create(userId, address));
    }

    @PutMapping("/addresses/{id}")
    public R<ShippingAddress> updateAddress(@PathVariable Long id, @RequestParam Long userId,
                                            @RequestBody ShippingAddress address) {
        address.setId(id);
        return R.ok(shippingAddressService.update(userId, address));
    }

    @DeleteMapping("/addresses/{id}")
    public R<?> deleteAddress(@PathVariable Long id, @RequestParam Long userId) {
        shippingAddressService.delete(userId, id);
        return R.ok();
    }

    @PutMapping("/addresses/{id}/default")
    public R<?> defaultAddress(@PathVariable Long id, @RequestParam Long userId) {
        shippingAddressService.setDefault(userId, id);
        return R.ok();
    }

    @GetMapping("/addresses/default-of/current")
    public R<ShippingAddress> defaultAddress(@RequestParam Long userId) {
        return R.ok(shippingAddressService.defaultOf(userId));
    }

    @GetMapping("/regions")
    public R<List<Region>> regions() {
        return R.ok(regionService.listAllOrdered());
    }

    @GetMapping("/admin/users")
    public R<PageVO<UserVO>> users(@RequestParam int page, @RequestParam int size,
                                   @RequestParam(required = false) String keyword) {
        return R.ok(userAdminService.list(page, size, keyword));
    }

    @GetMapping("/admin/users/{id}")
    public R<UserVO> user(@PathVariable Long id) {
        return R.ok(userAdminService.detail(id));
    }

    @PutMapping("/admin/users/{id}/status")
    public R<UserVO> setUserStatus(@PathVariable Long id, @RequestBody UserStatusRequest request) {
        return R.ok(userAdminService.setStatus(id, request));
    }
}
