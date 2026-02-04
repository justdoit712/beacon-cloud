package com.cz.api.advice;

import com.cz.api.utils.R;
import com.cz.api.vo.ResultVO;
import com.cz.common.exception.ApiException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResultVO apiExceptionHandler(ApiException ex){
        return R.error(ex);
    }
}
