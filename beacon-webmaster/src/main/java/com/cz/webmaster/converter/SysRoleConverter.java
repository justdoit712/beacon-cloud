package com.cz.webmaster.converter;

import com.cz.webmaster.controller.support.ControllerValueUtils;
import com.cz.webmaster.dto.SysRoleForm;
import com.cz.webmaster.entity.SmsRole;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SysRoleConverter {

    private SysRoleConverter() {
    }

    public static SmsRole toEntity(SysRoleForm form) {
        SmsRole role = new SmsRole();
        if (form == null) {
            return role;
        }
        role.setId(form.getId());
        role.setName(form.getName() == null ? null : form.getName().trim());
        role.setExtend1(form.getRemark());
        if (form.getStatus() != null) {
            role.setExtend2(String.valueOf(form.getStatus()));
        }
        return role;
    }

    public static Map<String, Object> toView(SmsRole role) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (role == null) {
            return data;
        }
        data.put("id", role.getId());
        data.put("name", role.getName());
        data.put("remark", role.getExtend1());
        data.put("status", ControllerValueUtils.parseInt(role.getExtend2(), 1));
        return data;
    }
}
