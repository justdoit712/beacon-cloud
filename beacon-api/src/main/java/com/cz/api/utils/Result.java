package com.cz.api.utils;

import com.cz.api.vo.ResultVO;
import com.cz.common.exception.BizException;

public class Result {

    public static ResultVO ok(){
        ResultVO r = new ResultVO();
        r.setCode(0);
        r.setMsg("接收成功");
        return r;
    }

    public static ResultVO error(Integer code,String msg){
        ResultVO r = new ResultVO();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static ResultVO error(BizException ex) {
        ResultVO r = new ResultVO();
        r.setCode(ex.getCode());
        r.setMsg(ex.getMessage());
        return r;
    }

}
