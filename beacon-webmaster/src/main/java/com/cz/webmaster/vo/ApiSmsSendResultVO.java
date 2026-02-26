package com.cz.webmaster.vo;

import lombok.Data;

/**
 * Response model from beacon-api SMS send endpoint.
 */
@Data
public class ApiSmsSendResultVO {

    private Integer code;

    private String msg;

    private String uid;

    private String sid;
}
