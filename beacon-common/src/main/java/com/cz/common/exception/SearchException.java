package com.cz.common.exception;

import com.cz.common.enums.ExceptionEnums;

/**
 * 搜索模块的异常对象
 * @author zjw
 * @description
 */
public class SearchException extends BizException {

    public SearchException(String message, Integer code) {
        super(message, code);
    }

    public SearchException(ExceptionEnums enums) {
        super(enums);
    }
}
