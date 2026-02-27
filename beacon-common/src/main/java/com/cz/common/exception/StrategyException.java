package com.cz.common.exception;

import com.cz.common.enums.ExceptionEnums;

public class StrategyException extends BizException {

    public StrategyException(String message, Integer code) {
        super(message, code);
    }

    public StrategyException(ExceptionEnums enums) {
        super(enums);
    }
}
