package com.cz.common.model.exception;

import com.cz.common.model.enums.ExceptionEnums;
import lombok.Getter;

@Getter
public class StrategyException extends RuntimeException {

    private Integer code;

    public StrategyException(String message, Integer code) {
        super(message);
        this.code = code;
    }


    public StrategyException(ExceptionEnums enums) {
        super(enums.getMsg());
        this.code = enums.getCode();
    }

}

