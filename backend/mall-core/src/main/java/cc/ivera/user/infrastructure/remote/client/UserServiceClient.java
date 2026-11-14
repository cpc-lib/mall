package cc.ivera.user.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.dto.*;
import cc.ivera.user.application.vo.LoginVO;
import cc.ivera.user.application.vo.PasswordResetHandleVO;
import cc.ivera.user.application.vo.UserVO;
import cc.ivera.user.domain.model.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户服务内部契约客户端：路径与参数对齐 mall-user-service 的 UserInternalController。
 */
@FeignClient(name = "mall-user-service", url = "${user.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface UserServiceClient {

    @PostMapping("/internal/user/auth/register")
    R<UserAccount> register(@RequestBody RegisterRequest request);

    @PostMapping("/internal/user/auth/login")
    R<LoginVO> login(@RequestBody LoginRequest request,
                     @RequestParam(value = "ip", required = false) String ip,
                     @RequestParam(value = "userAgent", required = false) String userAgent);

    @PostMapping("/internal/user/auth/refresh")
    R<LoginVO> refresh(@RequestBody RefreshTokenRequest request);

    @PostMapping("/internal/user/auth/logout")
    R<Object> logout(@RequestParam(value = "userId", required = false) Long userId,
                     @RequestParam(value = "sid", required = false) String sid,
                     @RequestBody(required = false) RefreshTokenRequest request);

    @PostMapping("/internal/user/auth/password")
    R<Object> changePassword(@RequestParam("userId") Long userId,
                             @RequestBody PasswordChangeRequest request);

    @PostMapping("/internal/user/auth/admin/reset-password")
    R<Object> adminResetPassword(@RequestBody AdminResetPasswordRequest request);

    @PostMapping("/internal/user/auth/verify-access-token")
    R<AuthPrincipal> verifyAccessToken(@RequestBody AccessTokenRequest request);

    @GetMapping("/internal/user/sessions/{userId}")
    R<List<AuthSession>> listSessionsByUser(@PathVariable("userId") Long userId);

    @DeleteMapping("/internal/user/sessions/{userId}/{sid}")
    R<Object> kick(@PathVariable("userId") Long userId, @PathVariable("sid") String sid);

    @DeleteMapping("/internal/user/sessions/{userId}")
    R<Object> kickAll(@PathVariable("userId") Long userId);

    @PostMapping("/internal/user/password-reset-requests")
    R<Object> submitPasswordReset(@RequestBody PasswordResetRequestSubmitRequest request);

    @GetMapping("/internal/user/password-reset-requests")
    R<List<PasswordResetRequest>> listPasswordResetRequests();

    @PostMapping("/internal/user/password-reset-requests/{id}/handle")
    R<PasswordResetHandleVO> handlePasswordReset(@PathVariable("id") Long id,
                                                 @RequestParam("operatorUserId") Long operatorUserId,
                                                 @RequestBody(required = false) PasswordResetHandleRequest request);

    @PostMapping("/internal/user/password-reset-requests/{id}/reject")
    R<Object> rejectPasswordReset(@PathVariable("id") Long id,
                                  @RequestParam("operatorUserId") Long operatorUserId,
                                  @RequestBody(required = false) PasswordResetRejectRequest request);

    @GetMapping("/internal/user/addresses")
    R<List<ShippingAddress>> listAddresses(@RequestParam("userId") Long userId);

    @GetMapping("/internal/user/addresses/{id}")
    R<ShippingAddress> getAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId);

    @PostMapping("/internal/user/addresses")
    R<ShippingAddress> createAddress(@RequestParam("userId") Long userId,
                                     @RequestBody ShippingAddress address);

    @PutMapping("/internal/user/addresses/{id}")
    R<ShippingAddress> updateAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId,
                                     @RequestBody ShippingAddress address);

    @DeleteMapping("/internal/user/addresses/{id}")
    R<Object> deleteAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId);

    @PutMapping("/internal/user/addresses/{id}/default")
    R<Object> setDefaultAddress(@PathVariable("id") Long id, @RequestParam("userId") Long userId);

    @GetMapping("/internal/user/addresses/default-of/current")
    R<ShippingAddress> defaultAddressOf(@RequestParam("userId") Long userId);

    @GetMapping("/internal/user/regions")
    R<List<Region>> listRegions();

    @GetMapping("/internal/user/admin/users")
    R<PageVO<UserVO>> listUsers(@RequestParam("page") int page, @RequestParam("size") int size,
                                @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/internal/user/admin/users/{id}")
    R<UserVO> getUserDetail(@PathVariable("id") Long id);

    @PutMapping("/internal/user/admin/users/{id}/status")
    R<UserVO> setUserStatus(@PathVariable("id") Long id, @RequestBody UserStatusRequest request);
}
