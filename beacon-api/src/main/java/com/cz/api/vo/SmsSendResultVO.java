package com.cz.api.vo;

import lombok.Data;

/**
 * 短信发送接口响应对象。
 *
 * <p>用于 `beacon-api` 发送短信相关接口返回受理结果，包含平台生成的
 * `sid`、调用方传入的 `uid` 以及扩展统计字段。</p>
 */
@Data
public class SmsSendResultVO {

    private Integer code;

    private String msg;

    private Integer count;

    private Long fee;

    private String uid;

    private String sid;
}
