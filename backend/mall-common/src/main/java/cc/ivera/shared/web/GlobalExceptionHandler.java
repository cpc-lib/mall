package cc.ivera.shared.web;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 统一异常处理入口（spec/planned/api/UNIFIED_ERROR_CODE_SPEC.md）：
 * 业务错误 -1、参数校验 400、系统异常 500，错误响应统一结构 { code, message, data:null }。
 * <p>
 * 外部 {@code /api/**} 保持 HTTP 200 + body code；内部 {@code /internal/**} 的系统异常
 * （code >= 500）映射为 HTTP 500，确保 OpenFeign {@code ErrorDecoder} 与链路追踪指标正常工作。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String INTERNAL_URI_PREFIX = "/internal/";

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<?>> handleBizException(BizException ex, HttpServletRequest request) {
        return respond(request, R.error(ex.getCode(), ex.getMessage()), ex.getCode().getCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                     HttpServletRequest request) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("；"));
        return respond(request, R.error(ErrorCode.PARAM_ERROR, message), ErrorCode.PARAM_ERROR.getCode());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<?>> handleBindException(BindException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("；"));
        return respond(request, R.error(ErrorCode.PARAM_ERROR, message), ErrorCode.PARAM_ERROR.getCode());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<?>> handleConstraintViolationException(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        String message = ex.getConstraintViolations()
            .stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining("；"));
        return respond(request, R.error(ErrorCode.PARAM_ERROR, message), ErrorCode.PARAM_ERROR.getCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<?>> handleException(Exception ex, HttpServletRequest request) {
        log.error("系统异常", ex);
        // 系统异常不透出内部细节（堆栈/SQL/中间件信息），统一返回通用文案，详情仅入日志
        return respond(request,
            R.error(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getDefaultMessage()),
            ErrorCode.SYSTEM_ERROR.getCode());
    }

    /**
     * 内部接口的系统异常使用真实 HTTP 错误码，使 Feign ErrorDecoder 与埋点能识别失败；
     * 外部接口维持既有 HTTP 200 契约不变。
     */
    private ResponseEntity<R<?>> respond(HttpServletRequest request, R<?> body, int code) {
        boolean internal = request != null
            && request.getRequestURI() != null
            && request.getRequestURI().startsWith(INTERNAL_URI_PREFIX);
        HttpStatus status = internal && code >= HttpStatus.INTERNAL_SERVER_ERROR.value()
            ? HttpStatus.INTERNAL_SERVER_ERROR
            : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + "：" + fieldError.getDefaultMessage();
    }
}
