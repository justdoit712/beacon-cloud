package com.cz.webmaster.dto;

import lombok.Data;

/**
 * 用户管理页面表单参数
 */
@Data
public class SysUserForm {

    private Integer id;

    private String usercode;

    private String password;

    private String email;

    private String realName;

    private Integer type;

    private Integer status;

    private Long clientid;
}
