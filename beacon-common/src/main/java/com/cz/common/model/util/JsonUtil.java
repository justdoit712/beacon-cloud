package com.cz.common.model.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author zjw
 * @description
 */
public class JsonUtil {

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static String obj2JSON(Object obj){
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException("转换JSON失败！");
        }
    }

}
