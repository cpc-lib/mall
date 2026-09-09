package cc.ivera.shared.security;

import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        if (isPublic(uri, request.getMethod())) return true;
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer "))
            return reject(response, ErrorCode.UNAUTHORIZED, "未登录或 Access Token 缺失");
        try {
            AuthPrincipal principal = authService.verifyAccessToken(authorization.substring(7).trim());
            if (requiresAdmin(uri, request.getMethod()) && !"ROLE_ADMIN".equals(principal.getRole()))
                return reject(response, ErrorCode.FORBIDDEN, "需要管理员权限");
            if (isUserOnlyShopping(uri) && "ROLE_ADMIN".equals(principal.getRole()))
                return reject(response, ErrorCode.FORBIDDEN, "管理员账号不支持购物车与下单操作");
            AuthContext.set(principal);
            return true;
        } catch (RuntimeException e) {
            return reject(response, ErrorCode.UNAUTHORIZED, e.getMessage() == null ? "登录已失效" : e.getMessage());
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isUserOnlyShopping(String uri) {
        return uri.startsWith("/api/cart") || uri.startsWith("/api/checkout");
    }

    private boolean isPublic(String uri, String method) {
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register") || uri.equals("/api/auth/refresh")
            || uri.equals("/api/auth/password-reset-request")) return true;
        if (uri.contains("/notify")) return true;
        if (uri.startsWith("/api/product") && "GET".equalsIgnoreCase(method)) return true;
        if ((uri.equals("/api/payment-app/list") || uri.equals("/api/payment-app/channels") || uri.startsWith("/api/payment-app/list-by-channel/")) && "GET".equalsIgnoreCase(method))
            return true;
        if (uri.startsWith("/api/auth/") || uri.startsWith("/api/cart") || uri.startsWith("/api/checkout") || uri.startsWith("/api/refund-applies") || uri.startsWith("/api/admin/") || uri.startsWith("/api/order") || uri.startsWith("/api/user/"))
            return false;
        return !uri.startsWith("/api/payment-app") && !uri.startsWith("/api/payment-channel") && !uri.startsWith("/api/payment-config") && !uri.startsWith("/api/reconciliation") && !uri.startsWith("/api/refund-info");
    }

    private boolean requiresAdmin(String uri, String method) {
        if (uri.startsWith("/api/auth/admin/") || uri.startsWith("/api/admin/") || uri.contains("/admin/")) return true;
        if (uri.startsWith("/api/refund-applies/") && (uri.endsWith("/accept") || uri.endsWith("/reject"))) return true;
        return uri.startsWith("/api/payment-app") || uri.startsWith("/api/payment-channel") || uri.startsWith("/api/payment-config") || uri.startsWith("/api/reconciliation") || uri.startsWith("/api/refund-info");
    }

    /**
     * 认证拦截失败统一响应：HTTP 状态与 ErrorCode 数值一致（401/403），body 复用 R 结构。
     */
    private boolean reject(HttpServletResponse response, ErrorCode errorCode, String message) throws Exception {
        response.setStatus(errorCode.getCode());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(R.error(errorCode, message)));
        return false;
    }
}
