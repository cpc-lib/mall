package cc.ivera.shared.security;

import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AccessTokenVerifier accessTokenVerifier;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(@Lazy @Autowired(required = false) AccessTokenVerifier accessTokenVerifier,
                           ObjectMapper objectMapper) {
        this.accessTokenVerifier = accessTokenVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        if (isPublic(uri, request.getMethod())) return true;
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || accessTokenVerifier == null)
            return reject(response, ErrorCode.UNAUTHORIZED, "未登录或 Access Token 缺失");
        try {
            AuthPrincipal principal = accessTokenVerifier.verifyAccessToken(authorization.substring(7).trim());
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

    /**
     * 严格白名单机制：默认不放行，仅对明确声明的公开路径返回 true。
     * <p>
     * 历史实现使用黑名单取反（"不在拦截列表也不在黑名单 = 公开"），导致新增路径默认被匿名裸奔。
     * 所有不在本方法里的 /api/** 路径都需要登录；管理接口还需在 requiresAdmin() 里二次校验。
     */
    private boolean isPublic(String uri, String method) {
        // CORS 预检
        if ("OPTIONS".equalsIgnoreCase(method)) return true;

        // 1. 用户认证：登录/注册/刷新/找回密码申请
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register")
            || uri.equals("/api/auth/refresh") || uri.equals("/api/auth/password-reset-request")) return true;

        // 2. 支付渠道异步回调（微信/支付宝服务器回调，不带 Authorization）
        if (uri.equals("/api/wx-pay/native/notify")          // 微信 V3 支付回调
            || uri.equals("/api/wx-pay-v2/native/notify")   // 微信 V2 支付回调
            || uri.equals("/api/wx-pay/refunds/notify")     // 微信退款回调
            || uri.equals("/api/ali-pay/trade/notify"))     // 支付宝支付回调
            return true;

        // 3. 商品浏览（GET 方法公开，写操作需登录）
        if (uri.startsWith("/api/product") && "GET".equalsIgnoreCase(method)) return true;

        // 4. 行政区划（用户端地址选择器，GET 公开）
        if (uri.startsWith("/api/regions") && "GET".equalsIgnoreCase(method)) return true;

        // 5. 支付渠道配置（仅 3 个只读 GET 供用户端选择支付方式）
        if ((uri.equals("/api/payment-app/list")
            || uri.equals("/api/payment-app/channels")
            || uri.startsWith("/api/payment-app/list-by-channel/"))
            && "GET".equalsIgnoreCase(method)) return true;

        // 6. Swagger 文档（开发环境）
        if (uri.startsWith("/doc.html") || uri.startsWith("/swagger-ui/")
            || uri.startsWith("/v3/api-docs") || uri.startsWith("/webjars/")) return true;

        // 7. 微信 JSAPI 支付回调（其余回调已在上方逐条声明，不做 /notify 兜底放行）
        if (uri.equals("/api/wx-pay/jsapi/notify/v1")) return true;

        // 默认：不放行，需要登录
        return false;
    }

    private boolean requiresAdmin(String uri, String method) {
        // 渠道账单下载/查询与渠道退款原始报文查询仅管理员：响应含商户全量交易/资金流水或渠道报文
        if (uri.startsWith("/api/wx-pay/querybill/") || uri.startsWith("/api/wx-pay/downloadbill/")
            || uri.startsWith("/api/ali-pay/bill/") || uri.startsWith("/api/wx-pay/query-refund/")
            || uri.startsWith("/api/ali-pay/trade/fastpay/refund/")) return true;
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
