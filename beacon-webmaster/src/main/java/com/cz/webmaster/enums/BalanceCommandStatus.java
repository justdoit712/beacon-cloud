package com.cz.webmaster.enums;

import com.cz.common.enums.ExceptionEnums;

/**
 * 余额命令执行状态。
 *
 * <p>该枚举用于表达余额命令的业务结果状态，
 * 避免上层调用方继续依赖字符串消息做分支判断。</p>
 */
public enum BalanceCommandStatus {

    /**
     * 执行成功。
     */
    SUCCESS(true, 0, "ok"),

    /**
     * 余额不足。
     */
    BALANCE_NOT_ENOUGH(false, ExceptionEnums.BALANCE_NOT_ENOUGH),

    /**
     * 客户不存在。
     */
    CLIENT_NOT_FOUND(false, -301, "client not found");

    /**
     * 是否成功。
     */
    private final boolean success;

    /**
     * 对外返回的状态码。
     */
    private final int code;

    /**
     * 对外返回的状态消息。
     */
    private final String message;

    BalanceCommandStatus(boolean success, int code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }

    BalanceCommandStatus(boolean success, ExceptionEnums exceptionEnum) {
        this(success, exceptionEnum.getCode(), exceptionEnum.getMsg());
    }

    /**
     * 返回是否成功。
     *
     * @return {@code true} 表示成功，{@code false} 表示失败
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 返回状态码。
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 返回状态消息。
     *
     * @return 状态消息
     */
    public String getMessage() {
        return message;
    }
}
