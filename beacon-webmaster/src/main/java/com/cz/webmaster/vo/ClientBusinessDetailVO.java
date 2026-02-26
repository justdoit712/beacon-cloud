package com.cz.webmaster.vo;

import lombok.Data;

/**
 * 客户业务详情视图
 */
@Data
public class ClientBusinessDetailVO {

    private Long id;

    private String corpname;

    private String usercode;

    private String pwd;

    private String ipaddress;

    private Byte isreturnstatus;

    private String receivestatusurl;

    private String mobile;

    private String priority;

    private String usertype;

    private String state;

    private String money;
}
