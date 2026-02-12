package com.cz.common.enums;

import lombok.Getter;

@Getter
public enum ExceptionEnums {
    UNKNOWN_ERROR(-100,"未知错误"),
    ERROR_APIKEY(-1,"非法的apikey"),
    IP_NOT_WHITE(-2,"请求的ip不在白名单内"),
    ERROR_SIGN(-3,"无可用签名"),
    ERROR_TEMPLATE(-4,"无可用模板"),
    ERROR_MOBILE(-5,"手机号格式不正确"),
    BALANCE_NOT_ENOUGH(-6,"手客户余额不足"),
    PARAMETER_ERROR(-10,"参数不合法！"),
    SNOWFLAKE_OUT_OF_RANGE(-11,"雪花算法生成的id超出范围！"),
    SNOWFLAKE_TIME_BACK(-12,"雪花算法生成的id出现时间回拨！"),
    ERROR_DIRTY_WORD (-13,"包含敏感词"),
    BLACK_GLOBAL(-14,"全局黑名单"),
    BLACK_CLIENT(-15,"客户黑名单"),
    ONE_MINUTE_LIMIT(-16,"一分钟限流规则"),
    ONE_HOUR_LIMIT(-17,"一小时限流规则"),
    NO_CHANNEL(-18,"没有可用通道"),
    SEARCH_INDEX_ERROR(-19,"添加文档信息失败！"),
    SEARCH_UPDATE_ERROR(-20,"修改文档信息失败！"),

    KAPACHA_ERROR(-100,"验证码错误！"),
    AUTHEN_ERROR(-101,"用户名或密码错误！"),
    NOT_LOGIN(-102,"用户未登录！"),
    USER_MENU_ERROR(-103,"查询用户的菜单信息失败！"),
    SMS_NO_AUTHOR(-104,"当前登录用户没有权限查询当前短信信息")
    ;


    private Integer code;

    private String msg;

    ExceptionEnums(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}