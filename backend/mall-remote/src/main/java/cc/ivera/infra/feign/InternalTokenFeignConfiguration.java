package cc.ivera.infra.feign;

import cc.ivera.shared.security.InternalTokenInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 内部调用令牌透传：所有 Feign 客户端在配置了 {@code internal.auth.token}
 * （环境变量 {@code INTERNAL_AUTH_TOKEN}）时自动附加 {@link InternalTokenInterceptor#HEADER}。
 * <p>
 * 微信/支付宝渠道客户端是独立 SDK/HTTP 客户端，不走 Feign，不受影响。
 * 未配置令牌时不附加 Header，服务端 {@code InternalTokenInterceptor} 也处于关闭状态，
 * 保持本地直连（{@code NACOS_ENABLED=false}）可用。
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class InternalTokenFeignConfiguration {

    @Bean
    public RequestInterceptor internalTokenPropagator(@Value("${internal.auth.token:}") String token) {
        return template -> {
            if (StringUtils.hasText(token)) {
                template.header(InternalTokenInterceptor.HEADER, token);
            }
        };
    }
}
