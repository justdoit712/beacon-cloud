package com.cz.webmaster.dto;

import lombok.Data;

/**
 * 菜单管理页面表单参数
 */
@Data
public class SysMenuForm {

    private Integer id;

    private String name;

    private Long parentId;

    private String parentName;

    private String url;

    private String icon;

    private Integer type;

    private String perms;

    private Integer orderNum;
}
