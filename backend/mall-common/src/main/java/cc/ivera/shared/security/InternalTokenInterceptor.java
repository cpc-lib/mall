package cc.ivera.shared.security;

import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部服务契约 {@code /internal/**} 的令牌校验。
 * <p>
 * 这些端点不经过网关，但内网任意主机仍可直连服务端口。开启后要求请求头
 * {@link #HEADER} 与配置 {@code internal.auth.token}（环境变量 {@code INTERNAL_AUTH_TOKEN}）常量时间相等；
 * 未配置令牌时保持原有行为（本地开发直连），生产必须通过环境变量注入。
 */
public class InternalTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;

    private final ObjectMapper objectMapper;

    public InternalTokenInterceptor(String token, ObjectMapper objectMapper) {
        this.expected = StringUtils.hasText(token) ? token.getBytes(StandardCharsets.UTF_8) : null;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        if (expected == null) {
            return true;
        }
        String actual = request.getHeader(HEADER);
        if (actual != null && MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.setStatus(ErrorCode.FORBIDDEN.getCode());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
            R.error(ErrorCode.FORBIDDEN, "内部调用令牌无效")));
        return false;
    }
}
