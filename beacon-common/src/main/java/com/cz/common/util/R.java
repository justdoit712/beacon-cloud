package com.cz.common.util;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.vo.ResultVO;

/**
 * 封装 ResultVO 的工具类
 */
public class R {

    /**
     * 成功，无数据
     */
    public static ResultVO ok() {
        return new ResultVO(0, "");
    }

    /**
     * 成功，指定消息
     */
    public static ResultVO ok(String msg) {
        return new ResultVO(0, msg);
    }

    /**
     * 成功，有数据
     */
    public static ResultVO ok(Object data) {
        ResultVO vo = ok();
        vo.setData(data);
        return vo;
    }

    /**
     * 成功，列表数据
     */
    public static ResultVO ok(Long total, Object rows) {
        ResultVO vo = ok();
        vo.setTotal(total);
        vo.setRows(rows);
        return vo;
    }

    /**
     * 失败，指定异常枚举
     */
    public static ResultVO error(ExceptionEnums enums) {
        return new ResultVO(enums.getCode(), enums.getMsg());
    }

    /**
     * 失败，指定错误消息（默认错误码 -1）
     */
    public static ResultVO error(String msg) {
        return new ResultVO(-1, msg);
    }

    /**
     * 失败，指定错误码和错误消息
     */
    public static ResultVO error(int code, String msg) {
        return new ResultVO(code, msg);
    }
}
