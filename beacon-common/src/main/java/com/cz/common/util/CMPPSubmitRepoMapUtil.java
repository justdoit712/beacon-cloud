package com.cz.common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.cz.common.model.StandardSubmit;

import java.time.Duration;

/**
 * 进程内暂存待关联的短信提交上下文。
 *
 * <p>网关提交 CMPP 请求后，可在运营商返回 SubmitResp 前按 sequence 取回原始
 * {@link StandardSubmit}。</p>
 *
 * @author cz
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
