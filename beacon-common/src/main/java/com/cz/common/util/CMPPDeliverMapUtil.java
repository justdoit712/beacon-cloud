package com.cz.common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.cz.common.model.StandardReport;

import java.time.Duration;

/**
 * 进程内暂存待关联的短信状态报告上下文。
 *
 * <p>在运营商返回最终状态报告前，按 msgId 暂存 {@link StandardReport}，
 * 供回执处理链路取回。</p>
 *
 * @author cz
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
