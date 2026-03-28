package com.cz.common.util;

import com.cz.common.exception.JsonSerializeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * 共享 JSON 序列化工具。
 *
 * <p>统一注册 Java 8 时间模块，并输出可读的日期时间字符串。</p>
 *
 * @author cz
 */
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {
    }

    public static String toJson(Object obj){
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonSerializeException("serialize object to json failed", e);
        }
    }

    public static Map<String, Object> toMap(Object obj) {
        try {
            return OBJECT_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException e) {
            throw new JsonSerializeException("convert object to map failed", e);
        }
    }

}
