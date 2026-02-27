package com.cz.common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.cz.common.model.StandardReport;

import java.time.Duration;

/**
 * 用于CMPP的状态回到时，获取核心信息的方式
 * @author cz
 * @description
 */
public final class CMPPDeliverMapUtil {

    private static final Cache<String, StandardReport> CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500_000)
            .build();

    private CMPPDeliverMapUtil() {
    }

    public static void put(String msgId, StandardReport submit){
        if (msgId == null) {
            return;
        }
        CACHE.put(msgId, submit);
    }

    public static StandardReport get(String msgId){
        if (msgId == null) {
            return null;
        }
        return CACHE.getIfPresent(msgId);
    }

    public static StandardReport remove(String msgId){
        if (msgId == null) {
            return null;
        }
        return CACHE.asMap().remove(msgId);
    }

    public static long size() {
        return CACHE.estimatedSize();
    }
}
