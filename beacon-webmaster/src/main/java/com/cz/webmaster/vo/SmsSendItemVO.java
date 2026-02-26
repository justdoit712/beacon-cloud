package com.cz.webmaster.vo;

import lombok.Data;

/**
 * Per-mobile send result.
 */
@Data
public class SmsSendItemVO {

    private String mobile;

    private Integer code;

    private String msg;

    private String sid;
}
