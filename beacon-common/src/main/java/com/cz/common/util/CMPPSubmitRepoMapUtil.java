package com.cz.common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.cz.common.model.StandardSubmit;

import java.time.Duration;

/**
 * 用于CMPP发送短信时，临时存储的位置
 * @author cz
 * @description
 */
public final class CMPPSubmitRepoMapUtil {

    private static final Cache<Integer, StandardSubmit> CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500_000)
            .build();

    private CMPPSubmitRepoMapUtil() {
    }

    public static void put(int sequence, StandardSubmit submit){
        CACHE.put(sequence, submit);
    }

    public static StandardSubmit get(int sequence){
        return CACHE.getIfPresent(sequence);
    }

    public static StandardSubmit remove(int sequence){
        return CACHE.asMap().remove(sequence);
    }

    public static long size() {
        return CACHE.estimatedSize();
    }
}
