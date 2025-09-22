package com.cz.api.enums;

import lombok.Getter;

/**
 * 一些响应信息中code和msg的对应
 * @author zjw
 * @description
 */
@Getter
public enum SmsCodeEnum {
    PARAMETER_ERROR(-10,"参数不合法！");

    private Integer code;
    private String msg;

    SmsCodeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}