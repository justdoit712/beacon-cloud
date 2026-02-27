package com.cz.api.advice;

import com.cz.api.utils.Result;
import com.cz.api.vo.ResultVO;
import com.cz.common.exception.BizException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResultVO apiExceptionHandler(BizException ex){
        return Result.error(ex);
    }
}
