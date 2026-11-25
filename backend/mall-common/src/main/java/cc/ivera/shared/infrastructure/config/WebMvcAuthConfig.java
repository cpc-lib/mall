package cc.ivera.shared.infrastructure.config;

import cc.ivera.shared.security.AuthInterceptor;
import cc.ivera.shared.security.InternalTokenInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final InternalTokenInterceptor internalTokenInterceptor;

    public WebMvcAuthConfig(AuthInterceptor authInterceptor,
                            ObjectMapper objectMapper,
                            @Value("${internal.auth.token:}") String internalToken) {
        this.authInterceptor = authInterceptor;
        this.internalTokenInterceptor = new InternalTokenInterceptor(internalToken, objectMapper);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
        // /internal/** 不经过网关，由内部令牌拦截器兜住直连调用
        registry.addInterceptor(internalTokenInterceptor).addPathPatterns("/internal/**");
    }
}
