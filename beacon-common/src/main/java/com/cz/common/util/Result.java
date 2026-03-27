package com.cz.common.util;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;

import java.util.List;

/**
 * 封装 ResultVO 的工具类
 */
public class Result {

    /**
     * 成功，无数据
     */
    public static ResultVO<Void> ok() {
        return new ResultVO<>(0, "");
    }

    /**
     * 成功，指定消息
     */
    public static ResultVO<Void> ok(String msg) {
        return new ResultVO<>(0, msg);
    }

    /**
     * 成功，有数据
     */
    public static <T> ResultVO<T> ok(T data) {
        ResultVO<T> vo = new ResultVO<>(0, "");
        vo.setData(data);
        return vo;
    }

    /**
     * 成功，列表数据
     */
    public static <T> PageResultVO<T> ok(Long total, List<T> rows) {
        PageResultVO<T> vo = new PageResultVO<>(0, "");
        vo.setTotal(total);
        vo.setRows(rows);
        return vo;
    }

    /**
     * 失败，指定异常枚举
     */
    public static ResultVO<Void> error(ExceptionEnums enums) {
        return new ResultVO<>(enums.getCode(), enums.getMsg());
    }

    /**
     * 失败，指定错误消息（默认错误码 -1）
     */
    public static ResultVO<Void> error(String msg) {
        return new ResultVO<>(-1, msg);
    }

    /**
     * 失败，指定错误码和错误消息
     */
    public static ResultVO<Void> error(int code, String msg) {
        return new ResultVO<>(code, msg);
    }

    /**
     * 分页失败，指定异常枚举。
     */
    public static <T> PageResultVO<T> errorPage(ExceptionEnums enums) {
        return new PageResultVO<>(enums.getCode(), enums.getMsg());
    }

    /**
     * 分页失败，指定错误消息（默认错误码 -1）。
     */
    public static <T> PageResultVO<T> errorPage(String msg) {
        return new PageResultVO<>(-1, msg);
    }
}
