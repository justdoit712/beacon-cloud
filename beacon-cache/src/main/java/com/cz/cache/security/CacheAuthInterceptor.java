package com.cz.cache.security;

import com.cz.common.security.CacheAuthHeaders;
import com.cz.common.security.CacheAuthSignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class CacheAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CacheAuthInterceptor.class);

    private final CacheSecurityProperties securityProperties;

    public CacheAuthInterceptor(CacheSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!securityProperties.isEnabled()) {
            return true;
        }

        String caller = request.getHeader(CacheAuthHeaders.HEADER_CALLER);
        String timestamp = request.getHeader(CacheAuthHeaders.HEADER_TIMESTAMP);
        String sign = request.getHeader(CacheAuthHeaders.HEADER_SIGN);

        if (!StringUtils.hasText(caller) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(sign)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "cache auth headers required");
            return false;
        }

        long timestampLong;
        try {
            timestampLong = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "cache auth timestamp invalid");
            return false;
        }

        long now = System.currentTimeMillis();
        long maxSkew = securityProperties.getMaxTimeSkewSeconds() * 1000;
        if (Math.abs(now - timestampLong) > maxSkew) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "cache auth timestamp expired");
            return false;
        }

        String secret = securityProperties.getCallerSecrets().get(caller);
        if (!StringUtils.hasText(secret)) {
            log.warn("cache auth failed: unknown caller {}", caller);
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "cache auth caller invalid");
            return false;
        }

        String payload = CacheAuthSignUtil.buildPayload(caller, timestamp, request.getMethod(), request.getRequestURI());
        String expected = CacheAuthSignUtil.sign(secret, payload);
        if (!safeEquals(expected, sign)) {
            log.warn("cache auth failed: bad signature caller={}, method={}, uri={}", caller, request.getMethod(), request.getRequestURI());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "cache auth signature invalid");
            return false;
        }

        CachePermission permission;
        try {
            permission = resolvePermission(request);
        } catch (IllegalStateException ex) {
            reject(response, HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
            return false;
        }
        if (!securityProperties.hasPermission(caller, permission)) {
            log.warn("cache auth forbidden: caller={}, permission={}, method={}, uri={}", caller, permission, request.getMethod(), request.getRequestURI());
            reject(response, HttpServletResponse.SC_FORBIDDEN, "cache permission denied");
            return false;
        }

        if (permission == CachePermission.WRITE || permission == CachePermission.KEYS || permission == CachePermission.TEST) {
            log.info("cache auth pass: caller={}, permission={}, method={}, uri={}", caller, permission, request.getMethod(), request.getRequestURI());
        }

        request.setAttribute(CacheAuthHeaders.REQUEST_ATTR_CALLER, caller);
        return true;
    }

    private CachePermission resolvePermission(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/test/")) {
            if (!securityProperties.isTestApiEnabled()) {
                throw new IllegalStateException("test api disabled");
            }
            return CachePermission.TEST;
        }
        if (uri.startsWith("/cache/keys")) {
            return CachePermission.KEYS;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return CachePermission.READ;
        }
        return CachePermission.WRITE;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
