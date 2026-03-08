package com.cz.webmaster.config;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.security.CacheAuthHeaders;
import com.cz.common.security.CacheAuthSignUtil;
import com.cz.webmaster.support.CacheSyncLogHelper;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * beacon-cache Feign 调用配置。
 * <p>
 * 统一提供三类能力：
 * <p>
 * 1) 内部鉴权头自动签名；<br>
 * 2) 连接/读取超时控制；<br>
 * 3) 远程调用异常日志输出。
 */
public class CacheFeignAuthConfig {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(CacheFeignAuthConfig.class);
    private static final AtomicBoolean INVALID_AUTH_CONFIG_LOGGED = new AtomicBoolean(false);

    @Value("${cache.client.auth.enabled:true}")
    private boolean enabled;

    @Value("${cache.client.auth.caller:}")
    private String caller;

    @Value("${cache.client.auth.secret:}")
    private String secret;

    @Value("${cache.client.feign.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${cache.client.feign.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public RequestInterceptor cacheAuthRequestInterceptor() {
        return template -> {
            if (!enabled) {
                return;
            }
            if (!StringUtils.hasText(caller) || !StringUtils.hasText(secret)) {
                if (INVALID_AUTH_CONFIG_LOGGED.compareAndSet(false, true)) {
                    CacheSyncLogHelper.error(
                            log,
                            "cache_sync",
                            "-",
                            CacheAuthSignUtil.normalizePath(template.path()),
                            "cache_auth_request_interceptor",
                            0L,
                            ExceptionEnums.CACHE_SYNC_CONFIG_INVALID,
                            "cache.client.auth.caller or cache.client.auth.secret is blank",
                            null
                    );
                }
                return;
            }
            String timestamp = String.valueOf(System.currentTimeMillis());
            String payload = CacheAuthSignUtil.buildPayload(
                    caller,
                    timestamp,
                    template.method(),
                    CacheAuthSignUtil.normalizePath(template.path())
            );
            String sign = CacheAuthSignUtil.sign(secret, payload);
            template.header(CacheAuthHeaders.HEADER_CALLER, caller);
            template.header(CacheAuthHeaders.HEADER_TIMESTAMP, timestamp);
            template.header(CacheAuthHeaders.HEADER_SIGN, sign);
        };
    }

    /**
     * 仅对引用本配置类的 FeignClient 生效。
     */
    @Bean
    public Request.Options cacheFeignRequestOptions() {
        return new Request.Options(connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 记录非 2xx 远程响应，便于定位缓存写删失败。
     */
    @Bean
    public ErrorDecoder cacheFeignErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            String path = response.request() == null ? "-" : extractPath(response.request().url());
            String message = "status=" + response.status() + ",reason=" + response.reason();
            ExceptionEnums errorEnum = methodKey != null && methodKey.contains("delete")
                    ? ExceptionEnums.CACHE_SYNC_DELETE_FAIL
                    : ExceptionEnums.CACHE_SYNC_WRITE_FAIL;
            CacheSyncLogHelper.error(
                    log,
                    "cache_sync",
                    "-",
                    path,
                    methodKey,
                    -1L,
                    errorEnum,
                    message,
                    null
            );
            return defaultDecoder.decode(methodKey, response);
        };
    }

    private static String extractPath(String url) {
        if (!StringUtils.hasText(url)) {
            return "-";
        }
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            return StringUtils.hasText(path) ? path : CacheAuthSignUtil.normalizePath(url);
        } catch (URISyntaxException ex) {
            return CacheAuthSignUtil.normalizePath(url);
        }
    }

    @Bean
    public Logger.Level cacheFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
