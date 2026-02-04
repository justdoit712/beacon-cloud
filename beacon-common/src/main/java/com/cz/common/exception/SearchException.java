package com.cz.common.exception;

import com.cz.common.enums.ExceptionEnums;
import lombok.Getter;

/**
 * 搜索模块的异常对象
 * @author zjw
 * @description
 */
@Getter
public class SearchException extends RuntimeException {

    private Integer code;

    public SearchException(String message, Integer code) {
        super(message);
        this.code = code;
    }


    public SearchException(ExceptionEnums enums) {
        super(enums.getMsg());
        this.code = enums.getCode();
    }

}
