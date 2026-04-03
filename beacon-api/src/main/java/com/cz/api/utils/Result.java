package com.cz.api.utils;

import com.cz.api.vo.SmsSendResultVO;
import com.cz.common.exception.BizException;

public class Result {

    public static SmsSendResultVO ok(){
        SmsSendResultVO r = new SmsSendResultVO();
        r.setCode(0);
        r.setMsg("接收成功");
        return r;
    }

    public static SmsSendResultVO error(Integer code,String msg){
        SmsSendResultVO r = new SmsSendResultVO();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static SmsSendResultVO error(BizException ex) {
        SmsSendResultVO r = new SmsSendResultVO();
        r.setCode(ex.getCode());
        r.setMsg(ex.getMessage());
        return r;
    }

}
