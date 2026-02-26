package com.cz.webmaster.converter;

import com.cz.webmaster.controller.support.ControllerValueUtils;
import com.cz.webmaster.dto.SysUserForm;
import com.cz.webmaster.entity.SmsUser;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SysUserConverter {

    private SysUserConverter() {
    }

    public static SmsUser toEntity(SysUserForm form) {
        SmsUser user = new SmsUser();
        if (form == null) {
            return user;
        }
        user.setId(form.getId());
        user.setUsername(form.getUsercode());
        user.setPassword(form.getPassword());
        user.setNickname(form.getRealName());
        user.setExtend1(form.getEmail());
        if (form.getType() != null) {
            user.setExtend2(String.valueOf(form.getType()));
        }
        if (form.getStatus() != null) {
            user.setExtend3(String.valueOf(form.getStatus()));
        }
        if (form.getClientid() != null) {
            user.setExtend4(String.valueOf(form.getClientid()));
        }
        return user;
    }

    public static Map<String, Object> toView(SmsUser user, boolean forEdit) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (user == null) {
            return data;
        }
        data.put("id", user.getId());
        data.put("usercode", user.getUsername());
        data.put("password", forEdit ? "" : user.getPassword());
        data.put("email", user.getExtend1());
        data.put("realName", user.getNickname());
        data.put("type", ControllerValueUtils.parseInt(user.getExtend2(), 2));
        data.put("status", ControllerValueUtils.parseInt(user.getExtend3(), 1));
        data.put("clientid", user.getExtend4());
        return data;
    }
}
