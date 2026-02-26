package com.cz.webmaster.dto;

import lombok.Data;

/**
 * SMS send request from webmaster page.
 */
@Data
public class SmsSendForm {

    /**
     * Target client business id.
     */
    private Long clientId;

    /**
     * Mobiles, supports multi-line / comma / blank separators.
     */
    private String mobile;

    /**
     * SMS content.
     */
    private String content;

    /**
     * 0-code, 1-notice, 2-marketing.
     */
    private Integer state;
}
