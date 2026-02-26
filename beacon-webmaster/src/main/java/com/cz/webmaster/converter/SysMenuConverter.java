package com.cz.webmaster.converter;

import com.cz.webmaster.controller.support.ControllerValueUtils;
import com.cz.webmaster.dto.SysMenuForm;
import com.cz.webmaster.entity.SmsMenu;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SysMenuConverter {

    private SysMenuConverter() {
    }

    public static SmsMenu toEntity(SysMenuForm form) {
        SmsMenu menu = new SmsMenu();
        if (form == null) {
            return menu;
        }
        menu.setId(form.getId());
        menu.setName(form.getName());
        menu.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        menu.setUrl(form.getUrl());
        menu.setIcon(form.getIcon());
        menu.setType(form.getType());
        menu.setSort(form.getOrderNum() == null ? 0 : form.getOrderNum());
        menu.setExtend1(form.getPerms());
        return menu;
    }

    public static Map<String, Object> toView(SmsMenu menu, Map<Integer, String> nameMap) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (menu == null) {
            return data;
        }
        data.put("id", menu.getId());
        data.put("name", menu.getName());
        data.put("parentId", menu.getParentId());
        data.put("parentName", nameMap.get(ControllerValueUtils.toInt(menu.getParentId())));
        data.put("url", menu.getUrl());
        data.put("icon", menu.getIcon());
        data.put("type", menu.getType());
        data.put("perms", menu.getExtend1());
        data.put("orderNum", menu.getSort());
        return data;
    }

    public static Map<Integer, String> buildNameMap(List<SmsMenu> menus) {
        Map<Integer, String> nameMap = new HashMap<>();
        nameMap.put(0, "一级菜单");
        for (SmsMenu menu : menus) {
            nameMap.put(menu.getId(), menu.getName());
        }
        return nameMap;
    }

    public static Map<String, Object> rootNode() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", 0);
        root.put("parentId", -1L);
        root.put("name", "一级菜单");
        return root;
    }

    public static Map<String, Object> toTreeNode(SmsMenu menu) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", menu.getId());
        node.put("parentId", menu.getParentId());
        node.put("name", menu.getName());
        return node;
    }
}
