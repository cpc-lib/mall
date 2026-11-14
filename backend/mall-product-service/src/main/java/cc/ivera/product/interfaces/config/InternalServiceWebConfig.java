package cc.ivera.product.interfaces.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalServiceWebConfig implements WebMvcConfigurer {
    private final InternalServiceAuthInterceptor interceptor;

    public InternalServiceWebConfig(InternalServiceAuthInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/internal/**");
    }
}
