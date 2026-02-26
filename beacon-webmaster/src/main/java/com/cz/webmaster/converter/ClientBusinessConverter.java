package com.cz.webmaster.converter;

import com.cz.webmaster.dto.ClientBusinessForm;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.vo.ClientBusinessDetailVO;
import com.cz.webmaster.vo.ClientBusinessVO;

/**
 * ClientBusiness entity/request/view conversions.
 */
public final class ClientBusinessConverter {

    private ClientBusinessConverter() {
    }

    public static ClientBusiness toEntity(ClientBusinessForm form) {
        ClientBusiness cb = new ClientBusiness();
        if (form == null) {
            return cb;
        }
        cb.setId(form.getId());
        cb.setCorpname(form.getCorpname());
        cb.setApikey(form.getUsercode());
        cb.setClientLinkname(form.getPwd());
        cb.setIpAddress(form.getIpaddress());
        cb.setIsCallback(form.getIsreturnstatus());
        cb.setCallbackUrl(form.getReceivestatusurl());
        cb.setClientPhone(form.getMobile());
        cb.setExtend1(form.getPriority());
        cb.setExtend2(form.getUsertype());
        cb.setExtend3(form.getState());
        cb.setExtend4(form.getMoney());
        return cb;
    }

    public static ClientBusinessDetailVO toDetailVO(ClientBusiness cb) {
        ClientBusinessDetailVO vo = new ClientBusinessDetailVO();
        if (cb == null) {
            return vo;
        }
        vo.setId(cb.getId());
        vo.setCorpname(cb.getCorpname());
        vo.setUsercode(cb.getApikey());
        vo.setPwd(cb.getClientLinkname());
        vo.setIpaddress(cb.getIpAddress());
        vo.setIsreturnstatus(cb.getIsCallback());
        vo.setReceivestatusurl(cb.getCallbackUrl());
        vo.setMobile(cb.getClientPhone());
        vo.setPriority(cb.getExtend1());
        vo.setUsertype(cb.getExtend2());
        vo.setState(cb.getExtend3());
        vo.setMoney(cb.getExtend4());
        return vo;
    }

    public static ClientBusinessVO toSimpleVO(ClientBusiness cb) {
        ClientBusinessVO vo = new ClientBusinessVO();
        if (cb == null) {
            return vo;
        }
        vo.setId(cb.getId());
        vo.setCorpname(cb.getCorpname());
        return vo;
    }
}
