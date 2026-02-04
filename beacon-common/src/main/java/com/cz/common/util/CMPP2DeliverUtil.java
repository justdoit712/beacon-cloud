package com.cz.common.util;

import com.cz.common.enums.CMPP2DeliverEnums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cz
 * @description
 */
public class CMPP2DeliverUtil {

    private static Map<String,String> stats = new HashMap<>();

    static{
        CMPP2DeliverEnums[] cmpp2DeliverEnums = CMPP2DeliverEnums.values();
        for (CMPP2DeliverEnums cmpp2DeliverEnum : cmpp2DeliverEnums) {
            stats.put(cmpp2DeliverEnum.getStat(),cmpp2DeliverEnum.getDescription());
        }
    }

    /**
     * 通过result结果拿到对应的错误信息
     * @param stat
     * @return
     */
    public static String getResultMessage(String stat){
        return stats.get(stat);
    }


}
