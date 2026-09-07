package cc.ivera.exception;

public class BizException extends RuntimeException {

    private final ErrorCode code;

    public BizException(String message) {
        this(ErrorCode.BIZ_ERROR, message);
    }

    public BizException(String message, Throwable cause) {
        this(ErrorCode.BIZ_ERROR, message, cause);
    }

    public BizException(ErrorCode code) {
        this(code, code.getDefaultMessage());
    }

    public BizException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
