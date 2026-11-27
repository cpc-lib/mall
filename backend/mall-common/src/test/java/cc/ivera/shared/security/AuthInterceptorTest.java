package cc.ivera.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证拦截白名单/管理员校验回归：
 * <ul>
 *   <li>默认拒绝：未声明的路径必须登录，不能因路径含 notify 等字样被放行；</li>
 *   <li>渠道账单仅管理员可访问；</li>
 *   <li>支付渠道异步回调保持免登录。</li>
 * </ul>
 */
class AuthInterceptorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthInterceptor interceptorFor(String token, AuthPrincipal principal) {
        AccessTokenVerifier verifier = t -> {
            if (token == null || !token.equals(t)) throw new IllegalStateException("token 无效");
            return principal;
        };
        return new AuthInterceptor(verifier, objectMapper);
    }

    private MockHttpServletRequest request(String method, String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    @DisplayName("渠道账单接口未登录返回 401")
    void billWithoutTokenRejected() throws Exception {
        AuthInterceptor interceptor = interceptorFor("t", null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean passed = interceptor.preHandle(
            request("GET", "/api/wx-pay/querybill/2026-01-01/tradebill", null), response, new Object());

        assertFalse(passed);
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("渠道账单接口普通用户返回 403，管理员放行")
    void billRequiresAdmin() throws Exception {
        AuthPrincipal user = new AuthPrincipal(1L, "u", "ROLE_USER", 0L, "s");
        AuthPrincipal admin = new AuthPrincipal(2L, "a", "ROLE_ADMIN", 0L, "s");

        MockHttpServletResponse userResponse = new MockHttpServletResponse();
        assertFalse(interceptorFor("ut", user).preHandle(
            request("GET", "/api/ali-pay/bill/downloadurl/query/2026-01-01/trade", "ut"), userResponse, new Object()));
        assertEquals(403, userResponse.getStatus());

        MockHttpServletResponse refundQueryUserResp = new MockHttpServletResponse();
        assertFalse(interceptorFor("ut", user).preHandle(
            request("GET", "/api/wx-pay/query-refund/REF123", "ut"), refundQueryUserResp, new Object()));
        assertEquals(403, refundQueryUserResp.getStatus());

        assertTrue(interceptorFor("at", admin).preHandle(
            request("GET", "/api/wx-pay/downloadbill/2026-01-01/tradebill", "at"),
            new MockHttpServletResponse(), new Object()));
        assertTrue(interceptorFor("at", admin).preHandle(
            request("GET", "/api/ali-pay/trade/fastpay/refund/REF123", "at"),
            new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("未声明路径默认拒绝，含 notify 字样也不能免登录")
    void unknownPathDefaultsToDeny() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean passed = interceptorFor("t", null).preHandle(
            request("POST", "/api/order-info/notify-test", null), response, new Object());

        assertFalse(passed);
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("支付渠道异步回调保持免登录")
    void paymentNotifyRemainsPublic() throws Exception {
        AuthInterceptor interceptor = interceptorFor("t", null);

        assertTrue(interceptor.preHandle(request("POST", "/api/wx-pay/native/notify", null),
            new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request("POST", "/api/wx-pay/jsapi/notify/v1", null),
            new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request("POST", "/api/ali-pay/trade/notify", null),
            new MockHttpServletResponse(), new Object()));
    }
}
