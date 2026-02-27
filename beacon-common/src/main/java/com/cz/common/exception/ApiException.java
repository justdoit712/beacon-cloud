package com.cz.common.exception;

import com.cz.common.enums.ExceptionEnums;

public class ApiException extends BizException {

    public ApiException(String message, Integer code) {
        super(message, code);
    }

    public ApiException(ExceptionEnums enums) {
        super(enums);
    }
}
