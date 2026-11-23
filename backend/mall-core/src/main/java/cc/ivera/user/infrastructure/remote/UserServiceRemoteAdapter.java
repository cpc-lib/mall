package cc.ivera.user.infrastructure.remote;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.*;
import cc.ivera.user.application.dto.*;
import cc.ivera.user.application.vo.LoginVO;
import cc.ivera.user.application.vo.PasswordResetHandleVO;
import cc.ivera.user.application.vo.UserVO;
import cc.ivera.user.domain.model.*;
import cc.ivera.user.infrastructure.remote.client.UserServiceClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserServiceRemoteAdapter implements AuthService, AuthSessionService, PasswordResetRequestService,
    ShippingAddressService, RegionService, UserAdminService {
    private final UserServiceClient client;

    public UserServiceRemoteAdapter(UserServiceClient client) {
        this.client = client;
    }

    @Override
    public UserAccount register(RegisterRequest request) {
        return unwrap(client.register(request));
    }

    @Override
    public LoginVO login(LoginRequest request, String ip, String userAgent) {
        return unwrap(client.login(request, ip, userAgent));
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        return unwrap(client.refresh(request));
    }

    @Override
    public void logout(AuthPrincipal principal, String refreshToken) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        unwrap(client.logout(principal == null ? null : principal.getUserId(),
            principal == null ? null : principal.getSid(), request));
    }

    @Override
    public void changePassword(AuthPrincipal principal, PasswordChangeRequest request) {
        unwrap(client.changePassword(principal.getUserId(), request));
    }

    @Override
    public void adminResetPassword(AdminResetPasswordRequest request) {
        unwrap(client.adminResetPassword(request));
    }

    @Override
    public AuthPrincipal verifyAccessToken(String token) {
        AccessTokenRequest request = new AccessTokenRequest();
        request.setAccessToken(token);
        return unwrap(client.verifyAccessToken(request));
    }

    @Override
    public List<AuthSession> listSessionsByUser(Long userId) {
        return unwrap(client.listSessionsByUser(userId));
    }

    @Override
    public void kick(Long userId, String sid) {
        unwrap(client.kick(userId, sid));
    }

    @Override
    public void kickAll(Long userId) {
        unwrap(client.kickAll(userId));
    }

    @Override
    public void submit(PasswordResetRequestSubmitRequest request) {
        unwrap(client.submitPasswordReset(request));
    }

    @Override
    public PageVO<PasswordResetRequest> list(int page, int size) {
        return unwrap(client.listPasswordResetRequests(page, size));
    }

    @Override
    public PasswordResetHandleVO handle(Long id, PasswordResetHandleRequest request, Long operatorUserId) {
        return unwrap(client.handlePasswordReset(id, operatorUserId, request));
    }

    @Override
    public void reject(Long id, PasswordResetRejectRequest request, Long operatorUserId) {
        unwrap(client.rejectPasswordReset(id, operatorUserId, request));
    }

    @Override
    public List<ShippingAddress> listByUser(Long userId) {
        return unwrap(client.listAddresses(userId));
    }

    @Override
    public ShippingAddress getById(Long userId, Long id) {
        return unwrap(client.getAddress(id, userId));
    }

    @Override
    public ShippingAddress create(Long userId, ShippingAddress addr) {
        return unwrap(client.createAddress(userId, addr));
    }

    @Override
    public ShippingAddress update(Long userId, ShippingAddress addr) {
        return unwrap(client.updateAddress(addr.getId(), userId, addr));
    }

    @Override
    public void delete(Long userId, Long id) {
        unwrap(client.deleteAddress(id, userId));
    }

    @Override
    public void setDefault(Long userId, Long id) {
        unwrap(client.setDefaultAddress(id, userId));
    }

    @Override
    public ShippingAddress defaultOf(Long userId) {
        return unwrap(client.defaultAddressOf(userId));
    }

    @Override
    public List<Region> listAllOrdered() {
        return unwrap(client.listRegions());
    }

    @Override
    public PageVO<UserVO> list(int page, int size, String keyword) {
        return unwrap(client.listUsers(page, size, keyword));
    }

    @Override
    public UserVO detail(Long id) {
        return unwrap(client.getUserDetail(id));
    }

    @Override
    public UserVO setStatus(Long id, UserStatusRequest request) {
        return unwrap(client.setUserStatus(id, request));
    }

    private <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("用户服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "用户服务调用失败" : message);
    }
}
