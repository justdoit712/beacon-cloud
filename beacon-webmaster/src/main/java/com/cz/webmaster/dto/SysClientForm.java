package com.cz.webmaster.dto;

import lombok.Data;

/**
 * 客户管理页面表单参数
 */
@Data
public class SysClientForm {

    private Long id;

    private String corpname;

    private String linkman;

    private String mobile;

    private String address;

    private String email;

    private String customermanager;
}
