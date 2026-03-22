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

/**
 * 缓存模块鉴权拦截器。
 *
 * <p>用于对进入缓存服务的请求执行统一鉴权，包括：</p>
 * <p>1. 校验调用方标识、时间戳和签名头是否齐全；</p>
 * <p>2. 校验时间戳是否在允许的漂移范围内；</p>
 * <p>3. 校验调用方签名是否合法；</p>
 * <p>4. 根据请求路径与 HTTP 方法解析所需权限并做授权判断。</p>
 *
 * <p>该拦截器只负责请求级安全校验，不负责具体缓存读写逻辑。</p>
 */
@Component
public class CacheAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CacheAuthInterceptor.class);

    private final CacheSecurityProperties securityProperties;

    /**
     * 创建缓存模块鉴权拦截器。
     *
     * @param securityProperties 缓存安全配置
     */
    public CacheAuthInterceptor(CacheSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * 在请求进入控制器前执行缓存服务鉴权。
     *
     * <p>当任一校验失败时，会直接向响应写入错误状态码与错误信息，
     * 并返回 {@code false} 阻止后续处理链继续执行。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 当前请求处理器
     * @return true 表示放行，false 表示拦截
     * @throws Exception 当写回拒绝响应失败时抛出
     */
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

    /**
     * 根据请求路径与方法解析当前请求所需的缓存访问权限。
     *
     * @param request 当前 HTTP 请求
     * @return 对应的缓存权限
     */
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

    /**
     * 以固定时间比较方式校验两个字符串是否相等。
     *
     * @param a 第一个字符串
     * @param b 第二个字符串
     * @return true 表示相等，false 表示不相等
     */
    private boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 向客户端写回拒绝响应。
     *
     * @param response 当前 HTTP 响应
     * @param status HTTP 状态码
     * @param message 错误信息
     * @throws Exception 当响应写出失败时抛出
     */
    private void reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
