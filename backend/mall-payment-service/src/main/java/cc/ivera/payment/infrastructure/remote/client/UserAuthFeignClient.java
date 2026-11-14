package cc.ivera.payment.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 用户服务内部契约客户端：路径与 {@code UserInternalController} 保持一一对应。
 */
@FeignClient(name = "mall-user-service", url = "${user.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface UserAuthFeignClient {

    @PostMapping("/internal/user/auth/verify-access-token")
    R<AuthPrincipal> verifyAccessToken(@RequestBody Map<String, Object> body);
}
