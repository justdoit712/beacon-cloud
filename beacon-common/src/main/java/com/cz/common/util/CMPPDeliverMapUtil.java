package com.cz.common.util;

import com.cz.common.model.StandardReport;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于CMPP的状态回到时，获取核心信息的方式
 * @author cz
 * @description
 */
public class CMPPDeliverMapUtil {

    private static ConcurrentHashMap<String, StandardReport> map = new ConcurrentHashMap<>();


    public static void put(String msgId,StandardReport submit){
        map.put(msgId,submit);
    }

    public static StandardReport get(String msgId){
        return map.get(msgId);
    }

    public static StandardReport remove(String msgId){
        return map.remove(msgId);
    }


}
