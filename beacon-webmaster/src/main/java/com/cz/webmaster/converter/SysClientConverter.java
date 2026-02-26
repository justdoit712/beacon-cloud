package com.cz.webmaster.converter;

import com.cz.webmaster.dto.SysClientForm;
import com.cz.webmaster.entity.ClientBusiness;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SysClientConverter {

    private SysClientConverter() {
    }

    public static ClientBusiness toEntity(SysClientForm form) {
        ClientBusiness cb = new ClientBusiness();
        if (form == null) {
            return cb;
        }
        cb.setId(form.getId());
        cb.setCorpname(form.getCorpname());
        cb.setClientLinkname(form.getLinkman());
        cb.setClientPhone(form.getMobile());
        cb.setExtend2(form.getAddress());
        cb.setExtend3(form.getEmail());
        cb.setExtend4(form.getCustomermanager());
        return cb;
    }

    public static Map<String, Object> toView(ClientBusiness cb) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (cb == null) {
            return data;
        }
        data.put("id", cb.getId());
        data.put("corpname", cb.getCorpname());
        data.put("address", cb.getExtend2());
        data.put("linkman", cb.getClientLinkname());
        data.put("mobile", cb.getClientPhone());
        data.put("email", cb.getExtend3());
        data.put("customermanager", cb.getExtend4());
        return data;
    }
}
