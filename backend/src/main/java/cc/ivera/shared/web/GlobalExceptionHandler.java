package cc.ivera.shared.web;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 统一异常处理入口（spec/planned/api/UNIFIED_ERROR_CODE_SPEC.md）：
 * 业务错误 -1、参数校验 400、系统异常 500，错误响应统一 HTTP 200 + { code, message, data:null }。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<?> handleBizException(BizException ex) {
        return R.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("；"));
        return R.error(ErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(BindException.class)
    public R<?> handleBindException(BindException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("；"));
        return R.error(ErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<?> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
            .stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining("；"));
        return R.error(ErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception ex) {
        log.error("系统异常", ex);
        // 系统异常不透出内部细节（堆栈/SQL/中间件信息），统一返回通用文案，详情仅入日志
        return R.error(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getDefaultMessage());
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + "：" + fieldError.getDefaultMessage();
    }
}
