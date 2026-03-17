package com.cz.webmaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 缓存同步配置模型。
 *
 * <p>用于统一承接 {@code sync.*} 配置，避免同步链路在多个类中直接读取零散配置。</p>
 *
 * <p>对应配置示例：</p>
 * <pre>
 * sync:
 *   enabled: true
 *   redis:
 *     namespace: beacon:dev:beacon-cloud:cz:
 *   runtime:
 *     enabled: true
 *   manual:
 *     enabled: true
 *   boot:
 *     enabled: false
 *     domains:
 *       - client_business
 *       - client_channel
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "sync")
public class CacheSyncProperties {

    /** Spring 环境对象，用于读取与缓存侧对齐的配置值。 */
    private final Environment environment;

    /**
     * 创建同步配置对象。
     *
     * @param environment Spring 环境对象
     */
    public CacheSyncProperties(Environment environment) {
        this.environment = environment;
    }

    /**
     * 同步总开关。
     *
     * <p>为 {@code false} 时，runtime/manual/boot 子开关均不生效。</p>
     */
    private boolean enabled = true;

    /** Redis 相关配置。 */
    private Redis redis = new Redis();

    /** 运行时同步配置。 */
    private Runtime runtime = new Runtime();

    /** 手工重建配置。 */
    private Manual manual = new Manual();

    /** 启动校准配置。 */
    private Boot boot = new Boot();

    /**
     * 在初始化后执行配置校验与规范化。
     *
     * <p>当前校验规则：</p>
     * <p>1. 当 {@code sync.enabled=true} 时，{@code sync.redis.namespace} 不能为空；</p>
     * <p>2. {@code sync.redis.namespace} 会被规范化为以冒号结尾；</p>
     * <p>3. 规范化后的 {@code sync.redis.namespace} 必须与
     * {@code cache.namespace.fullPrefix} 保持一致。</p>
     */
    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        if (redis == null) {
            throw new IllegalArgumentException("sync.redis must not be null");
        }
        redis.setNamespace(normalizeNamespace(redis.getNamespace(), "sync.redis.namespace"));
        validateNamespaceConsistency(
                resolveNamespace(),
                environment == null ? null : environment.getProperty("cache.namespace.fullPrefix")
        );
    }

    /**
     * 返回规范化后的 Redis 命名空间前缀。
     *
     * @return 以冒号结尾的命名空间前缀
     */
    public String resolveNamespace() {
        return normalizeNamespace(redis == null ? null : redis.getNamespace(), "sync.redis.namespace");
    }

    /**
     * 规范化命名空间前缀。
     *
     * @param namespace 原始命名空间前缀
     * @param name 配置项名称
     * @return 规范化后的命名空间前缀
     */
    private static String normalizeNamespace(String namespace, String name) {
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String value = namespace.trim();
        return value.endsWith(":") ? value : value + ":";
    }

    /**
     * 校验业务侧同步前缀与缓存侧物理前缀是否一致。
     *
     * @param syncNamespace 业务侧同步前缀
     * @param cacheNamespaceFullPrefix 缓存侧完整物理前缀
     */
    private static void validateNamespaceConsistency(String syncNamespace, String cacheNamespaceFullPrefix) {
        String cacheNamespace = normalizeNamespace(cacheNamespaceFullPrefix, "cache.namespace.fullPrefix");
        if (!syncNamespace.equals(cacheNamespace)) {
            throw new IllegalArgumentException(
                    "sync.redis.namespace must match cache.namespace.fullPrefix, sync="
                            + syncNamespace + ", cache=" + cacheNamespace
            );
        }
    }

    /**
     * 返回同步总开关。
     *
     * @return true 表示启用同步
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置同步总开关。
     *
     * @param enabled 是否启用同步
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 Redis 相关配置。
     *
     * @return Redis 配置对象
     */
    public Redis getRedis() {
        return redis;
    }

    /**
     * 设置 Redis 相关配置。
     *
     * @param redis Redis 配置对象
     */
    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    /**
     * 返回运行时同步配置。
     *
     * @return 运行时同步配置
     */
    public Runtime getRuntime() {
        return runtime;
    }

    /**
     * 设置运行时同步配置。
     *
     * @param runtime 运行时同步配置
     */
    public void setRuntime(Runtime runtime) {
        this.runtime = runtime;
    }

    /**
     * 返回手工重建配置。
     *
     * @return 手工重建配置
     */
    public Manual getManual() {
        return manual;
    }

    /**
     * 设置手工重建配置。
     *
     * @param manual 手工重建配置
     */
    public void setManual(Manual manual) {
        this.manual = manual;
    }

    /**
     * 返回启动校准配置。
     *
     * @return 启动校准配置
     */
    public Boot getBoot() {
        return boot;
    }

    /**
     * 设置启动校准配置。
     *
     * @param boot 启动校准配置
     */
    public void setBoot(Boot boot) {
        this.boot = boot;
    }

    /**
     * Redis 配置段。
     */
    public static class Redis {

        /**
         * Redis 命名空间完整前缀。
         *
         * <p>该值应与 {@code cache.namespace.fullPrefix} 保持一致。</p>
         */
        private String namespace = "beacon:dev:beacon-cloud:cz:";

        /**
         * 返回 Redis 命名空间前缀。
         *
         * @return Redis 命名空间前缀
         */
        public String getNamespace() {
            return namespace;
        }

        /**
         * 设置 Redis 命名空间前缀。
         *
         * @param namespace Redis 命名空间前缀
         */
        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }

    /**
     * 运行时同步配置段。
     */
    public static class Runtime {
        /** 是否启用运行时同步。 */
        private boolean enabled = true;

        /**
         * 返回运行时同步开关。
         *
         * @return true 表示启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置运行时同步开关。
         *
         * @param enabled 是否启用运行时同步
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 手工重建配置段。
     */
    public static class Manual {
        /** 是否启用手工重建。 */
        private boolean enabled = true;

        /**
         * 返回手工重建开关。
         *
         * @return true 表示启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置手工重建开关。
         *
         * @param enabled 是否启用手工重建
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 启动校准配置段。
     */
    public static class Boot {

        /** 是否启用启动校准。 */
        private boolean enabled = false;

        /**
         * 启动校准目标域列表。
         *
         * <p>若为空，则表示由调用方决定默认域范围。</p>
         */
        private List<String> domains = new ArrayList<>();

        /**
         * 返回启动校准开关。
         *
         * @return true 表示启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置启动校准开关。
         *
         * @param enabled 是否启用启动校准
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回启动校准目标域列表。
         *
         * @return 启动校准目标域列表
         */
        public List<String> getDomains() {
            return domains;
        }

        /**
         * 设置启动校准目标域列表。
         *
         * @param domains 启动校准目标域列表
         */
        public void setDomains(List<String> domains) {
            this.domains = domains == null ? new ArrayList<>() : domains;
        }
    }
}
