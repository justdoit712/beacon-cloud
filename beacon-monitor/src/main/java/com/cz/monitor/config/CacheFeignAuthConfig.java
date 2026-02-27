package com.cz.monitor.config;

import com.cz.common.security.CacheAuthHeaders;
import com.cz.common.security.CacheAuthSignUtil;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

public class CacheFeignAuthConfig {

    @Value("${cache.client.auth.enabled:true}")
    private boolean enabled;

    @Value("${cache.client.auth.caller:}")
    private String caller;

    @Value("${cache.client.auth.secret:}")
    private String secret;

    @Bean
    public RequestInterceptor cacheAuthRequestInterceptor() {
        return template -> {
            if (!enabled || !StringUtils.hasText(caller) || !StringUtils.hasText(secret)) {
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
}
