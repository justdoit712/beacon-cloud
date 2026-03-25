package com.cz.common.exception;

import com.cz.common.enums.ExceptionEnums;

/**
 * 搜索模块使用的业务异常。
 *
 * @author cz
 */
public class SearchException extends BizException {

    public SearchException(String message, Integer code) {
        super(message, code);
    }

    public SearchException(ExceptionEnums enums) {
        super(enums);
    }
}
