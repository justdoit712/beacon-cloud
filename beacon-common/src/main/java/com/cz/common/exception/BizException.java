package com.cz.common.exception;

import com.cz.common.enums.ExceptionEnums;
import lombok.Getter;

@Getter
public abstract class BizException extends RuntimeException {

    private final Integer code;

    protected BizException(String message, Integer code) {
        super(message);
        this.code = code;
    }

    protected BizException(ExceptionEnums enums) {
        super(enums.getMsg());
        this.code = enums.getCode();
    }
}
