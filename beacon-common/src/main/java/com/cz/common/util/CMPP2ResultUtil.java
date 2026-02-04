package com.cz.common.util;

import com.cz.common.enums.CMPP2ResultEnums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cz
 * @description
 */
public class CMPP2ResultUtil {

    private static Map<Integer,String> results = new HashMap<>();

    static{
        CMPP2ResultEnums[] cmpp2ResultEnums = CMPP2ResultEnums.values();
        for (CMPP2ResultEnums cmpp2ResultEnum : cmpp2ResultEnums) {
            results.put(cmpp2ResultEnum.getResult(),cmpp2ResultEnum.getMsg());
        }
    }

    /**
     * 通过result结果拿到对应的错误信息
     * @param result
     * @return
     */
    public static String getResultMessage(Integer result){
        return results.get(result);
    }


}
