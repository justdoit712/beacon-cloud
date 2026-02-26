package com.cz.webmaster.dto;

import lombok.Data;

/**
 * Request body for beacon-api internal single send endpoint.
 */
@Data
public class ApiInternalSingleSendForm {

    private String apikey;

    private String mobile;

    private String text;

    private String uid;

    private Integer state;

    private String realIp;
}
