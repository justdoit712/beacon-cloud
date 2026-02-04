package com.cz.common.util;

import com.cz.common.model.StandardSubmit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于CMPP发送短信时，临时存储的位置
 * @author cz
 * @description
 */
public class CMPPSubmitRepoMapUtil {

    private static ConcurrentHashMap<String, StandardSubmit> map = new ConcurrentHashMap<>();


    public static void put(int sequence,StandardSubmit submit){
        map.put(sequence + "",submit);
    }

    public static StandardSubmit get(int sequence){
        return map.get(sequence + "");
    }

    public static StandardSubmit remove(int sequence){
        return map.remove(sequence + "");
    }


}
