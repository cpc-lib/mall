package cc.ivera.security;

import cc.ivera.exception.BizException;

public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();
    private AuthContext() {}
    public static void set(AuthPrincipal principal) { HOLDER.set(principal); }
    public static AuthPrincipal get() { return HOLDER.get(); }
    public static Long userId() {
        AuthPrincipal p = HOLDER.get();
        if (p == null) throw new BizException("未登录或登录已失效");
        return p.getUserId();
    }
    public static void clear() { HOLDER.remove(); }
}
