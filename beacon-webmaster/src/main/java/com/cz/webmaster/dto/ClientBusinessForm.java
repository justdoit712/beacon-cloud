package com.cz.webmaster.dto;

import lombok.Data;

/**
 * 客户业务管理页面表单参数
 */
@Data
public class ClientBusinessForm {

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
