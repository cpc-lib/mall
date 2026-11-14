package cc.ivera.order.security;

import cc.ivera.order.infrastructure.remote.client.UserServiceClient;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.security.AccessTokenVerifier;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.dto.AccessTokenRequest;
import org.springframework.stereotype.Component;

/**
 * 订单服务复用用户服务的 Access Token 校验，保持与 BFF 一致的鉴权语义。
 */
@Component
public class UserAuthRemoteVerifier implements AccessTokenVerifier {

    private final UserServiceClient client;

    public UserAuthRemoteVerifier(UserServiceClient client) {
        this.client = client;
    }

    @Override
    public AuthPrincipal verifyAccessToken(String token) {
        AccessTokenRequest request = new AccessTokenRequest();
        request.setAccessToken(token == null ? "" : token);
        R<AuthPrincipal> response = client.verifyAccessToken(request);
        if (response == null) throw new BizException(ErrorCode.UNAUTHORIZED, "用户服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw new BizException(ErrorCode.UNAUTHORIZED, response.getMessage() == null ? "登录已失效" : response.getMessage());
        return response.getData();
    }
}
