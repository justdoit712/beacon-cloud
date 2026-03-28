package com.cz.webmaster.advice;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class WebmasterExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), firstError(ex.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public ResultVO<Void> handleBindException(BindException ex) {
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), firstError(ex.getBindingResult()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResultVO<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), "缺少参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResultVO<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), "参数类型不合法: " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResultVO<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return Result.error(ExceptionEnums.PARAMETER_ERROR);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResultVO<Void> handleConstraintViolation(ConstraintViolationException ex) {
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            if (violation != null && StringUtils.hasText(violation.getMessage())) {
                return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), violation.getMessage());
            }
        }
        return Result.error(ExceptionEnums.PARAMETER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResultVO<Void> handleIllegalArgument(IllegalArgumentException ex) {
        String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ExceptionEnums.PARAMETER_ERROR.getMsg();
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), message);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResultVO<Void> handleAuthorizationException(AuthorizationException ex) {
        log.warn("webmaster authorization denied: {}", ex.getMessage());
        return Result.error(ExceptionEnums.SMS_NO_AUTHOR);
    }

    @ExceptionHandler(Exception.class)
    public ResultVO<Void> handleUnknownException(Exception ex) {
        log.error("unhandled webmaster exception", ex);
        return Result.error(ExceptionEnums.UNKNOWN_ERROR);
    }

    private String firstError(BindingResult bindingResult) {
        if (bindingResult == null || bindingResult.getFieldError() == null) {
            return ExceptionEnums.PARAMETER_ERROR.getMsg();
        }
        String message = bindingResult.getFieldError().getDefaultMessage();
        return StringUtils.hasText(message) ? message : ExceptionEnums.PARAMETER_ERROR.getMsg();
    }
}
