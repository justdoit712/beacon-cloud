package com.cz.common.security;

/**
 * 缓存服务鉴权相关请求头常量。
 *
 * <p>该类用于统一定义 `beacon-webmaster` 等内部调用方向 `beacon-cache`
 * 发起请求时所使用的鉴权头名称，以及缓存服务在请求作用域内透传调用方身份时
 * 使用的属性名。</p>
 *
 * <p>统一收口这些常量的目的有两点：</p>
 * <p>1. 避免不同模块手写字符串造成头名漂移；</p>
 * <p>2. 让签名生成、拦截校验、日志记录等链路共享同一套命名约定。</p>
 */
public final class CacheAuthHeaders {

    /**
     * 私有构造方法，禁止实例化工具常量类。
     */
    private CacheAuthHeaders() {
    }

    /**
     * 缓存服务内部调用方标识请求头。
     *
     * <p>调用方使用该请求头声明自己的 caller 名称，
     * 缓存服务据此查找对应的密钥和权限配置。</p>
     */
    public static final String HEADER_CALLER = "X-Cache-Caller";

    /**
     * 缓存服务请求时间戳请求头。
     *
     * <p>该值参与签名计算，并用于服务端校验请求是否超出允许时间漂移范围，
     * 以降低重放请求风险。</p>
     */
    public static final String HEADER_TIMESTAMP = "X-Cache-Timestamp";

    /**
     * 缓存服务签名请求头。
     *
     * <p>调用方基于 caller、timestamp、HTTP method、请求路径等信息
     * 计算签名后放入该请求头，服务端会使用同一规则进行验签。</p>
     */
    public static final String HEADER_SIGN = "X-Cache-Sign";

    /**
     * 请求作用域内保存调用方标识的属性名。
     *
     * <p>缓存服务鉴权通过后，会把 caller 写入当前请求属性，
     * 供后续控制器、日志或审计逻辑读取。</p>
     */
    public static final String REQUEST_ATTR_CALLER = "cache.auth.caller";
}
