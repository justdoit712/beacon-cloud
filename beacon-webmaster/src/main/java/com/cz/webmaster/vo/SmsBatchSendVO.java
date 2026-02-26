package com.cz.webmaster.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch send summary for webmaster.
 */
@Data
public class SmsBatchSendVO {

    private Long clientId;

    private String clientName;

    private Integer state;

    private Integer total = 0;

    private Integer success = 0;

    private Integer failed = 0;

    private String message;

    private List<SmsSendItemVO> items = new ArrayList<>();
}
