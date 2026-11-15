package cc.ivera.bill.infrastructure.security;

import cc.ivera.bill.infrastructure.remote.client.UserAuthFeignClient;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.security.AccessTokenVerifier;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.R;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 账单服务复用用户服务的 Access Token 校验，保持与其它服务一致的鉴权语义。
 */
@Component
public class UserAuthRemoteVerifier implements AccessTokenVerifier {

    private final UserAuthFeignClient client;

    public UserAuthRemoteVerifier(UserAuthFeignClient client) {
        this.client = client;
    }

    @Override
    public AuthPrincipal verifyAccessToken(String token) {
        R<AuthPrincipal> response = client.verifyAccessToken(Map.of("accessToken", token == null ? "" : token));
        if (response == null) throw new BizException(ErrorCode.UNAUTHORIZED, "用户服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw new BizException(ErrorCode.UNAUTHORIZED, response.getMessage() == null ? "登录已失效" : response.getMessage());
        return response.getData();
    }
}
