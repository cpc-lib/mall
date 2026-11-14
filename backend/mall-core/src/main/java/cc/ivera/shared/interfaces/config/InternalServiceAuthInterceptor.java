package cc.ivera.shared.interfaces.config;

import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceAuthInterceptor implements HandlerInterceptor {
    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public InternalServiceAuthInterceptor(@Value("${internal.service.token:}") String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String actualToken = request.getHeader("X-Internal-Service-Token");
        if (expectedToken != null && !expectedToken.isBlank() && actualToken != null
            && MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8), actualToken.getBytes(StandardCharsets.UTF_8)))
            return true;
        response.setStatus(ErrorCode.UNAUTHORIZED.getCode());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(R.error(ErrorCode.UNAUTHORIZED, "内部服务认证失败")));
        return false;
    }
}
