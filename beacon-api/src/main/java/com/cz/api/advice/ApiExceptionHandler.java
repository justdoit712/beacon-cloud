package com.cz.api.advice;

import com.cz.api.utils.Result;
import com.cz.api.vo.ResultVO;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResultVO handleBizException(BizException ex){
        log.warn("api biz exception, code={}, msg={}", ex.getCode(), ex.getMessage());
        return Result.error(ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldError() == null
                ? ExceptionEnums.PARAMETER_ERROR.getMsg()
                : ex.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("api request validation failed: {}", msg);
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public ResultVO handleBindException(BindException ex) {
        String msg = ex.getBindingResult().getFieldError() == null
                ? ExceptionEnums.PARAMETER_ERROR.getMsg()
                : ex.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("api bind validation failed: {}", msg);
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResultVO handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("api request body parse failed", ex);
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResultVO handleIllegalArgumentException(IllegalArgumentException ex) {
        String msg = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ExceptionEnums.PARAMETER_ERROR.getMsg();
        log.warn("api illegal argument: {}", msg);
        return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public ResultVO handleUnknownException(Exception ex) {
        log.error("api unexpected exception", ex);
        return Result.error(ExceptionEnums.UNKNOWN_ERROR.getCode(), ExceptionEnums.UNKNOWN_ERROR.getMsg());
    }
}
