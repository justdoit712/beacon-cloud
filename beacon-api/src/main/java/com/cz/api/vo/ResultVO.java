package com.cz.api.vo;

import lombok.Data;

/**
 * @author cz
 * @description
 */
@Data
public class ResultVO {

    private Integer code;

    private String msg;

    private Integer count;

    private Long fee;

    private String uid;

    private String sid;
}