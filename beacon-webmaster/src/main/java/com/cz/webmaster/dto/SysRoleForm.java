package com.cz.webmaster.dto;

import lombok.Data;

/**
 * 角色管理页面表单参数
 */
@Data
public class SysRoleForm {

    private Integer id;

    private String name;

    private String remark;

    private Integer status;
}
