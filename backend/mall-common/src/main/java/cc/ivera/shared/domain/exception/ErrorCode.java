package cc.ivera.shared.domain.exception;

/**
 * 统一错误码契约（spec/planned/api/UNIFIED_ERROR_CODE_SPEC.md）。
 * 0=成功；-1=业务错误；400=参数校验；401=未认证；403=无权限；404=资源不存在；500=系统异常。
 * 错误响应统一结构：{ code, message, data:null }；业务/校验/系统错误 HTTP 200，认证拦截 HTTP 401/403。
 */
public enum ErrorCode {

    /**
     * 业务错误：BizException 默认码，业务规则校验失败。
     */
    BIZ_ERROR(-1, "业务处理失败"),

    /**
     * 参数校验失败：@Valid / @Validated 校验不通过。
     */
    PARAM_ERROR(400, "参数校验失败"),

    /**
     * 未认证：Access Token 缺失或失效，HTTP 401。
     */
    UNAUTHORIZED(401, "未登录或登录已失效"),

    /**
     * 无权限：角色权限不足，HTTP 403。
     */
    FORBIDDEN(403, "无权限访问"),

    /**
     * 资源不存在。
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 系统异常：未预期的服务端错误。
     */
    SYSTEM_ERROR(500, "系统异常");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
