package com.cz.webmaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 缓存同步配置模型。
 * <p>
 * 统一承载第一层要求的同步开关与命名空间配置，避免后续组件散落读取裸字符串配置。
 * <p>
 * 对应配置示例：
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
 *       - client_sign
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "sync")
public class CacheSyncProperties {

    /**
     * 全局总开关。
     * <p>
     * false：关闭全部同步能力，子开关不再生效。<br>
     * true：按 runtime/manual/boot 子开关分别控制。
     */
    private boolean enabled = true;

    /**
     * Redis 相关配置。
     */
    private Redis redis = new Redis();

    /**
     * 运行时同步开关（业务 save/update/delete 触发）。
     */
    private Runtime runtime = new Runtime();

    /**
     * 手工重建开关（管理入口触发）。
     */
    private Manual manual = new Manual();

    /**
     * 启动校准开关（服务启动触发）。
     */
    private Boot boot = new Boot();

    /**
     * 启动后做一次配置校验与规范化。
     * <p>
     * 当前硬性约束：当 sync.enabled=true 时，sync.redis.namespace 不能为空白。
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
    }

    /**
     * 解析 Redis 命名空间前缀。
     * <p>
     * 返回值保证以冒号结尾，便于 key 拼接时统一格式。
     *
     * @return 规范化后的命名空间前缀，例如 beacon:dev:beacon-cloud:cz:
     */
    public String resolveNamespace() {
        return normalizeNamespace(redis == null ? null : redis.getNamespace(), "sync.redis.namespace");
    }

    private static String normalizeNamespace(String namespace, String name) {
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String value = namespace.trim();
        return value.endsWith(":") ? value : value + ":";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public void setRuntime(Runtime runtime) {
        this.runtime = runtime;
    }

    public Manual getManual() {
        return manual;
    }

    public void setManual(Manual manual) {
        this.manual = manual;
    }

    public Boot getBoot() {
        return boot;
    }

    public void setBoot(Boot boot) {
        this.boot = boot;
    }

    /**
     * Redis 配置段。
     */
    public static class Redis {

        /**
         * Redis 命名空间前缀。
         * <p>
         * 约定格式：beacon:{env}:{project}:{owner}:
         */
        private String namespace = "beacon:dev:beacon-cloud:cz:";

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }

    /**
     * 运行时同步配置段。
     */
    public static class Runtime {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 手工重建配置段。
     */
    public static class Manual {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 启动校准配置段。
     */
    public static class Boot {

        /**
         * 是否启用启动校准。
         */
        private boolean enabled = false;

        /**
         * 启动校准的目标域清单。
         * <p>
         * 留空表示由调用方决定默认域策略。
         */
        private List<String> domains = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getDomains() {
            return domains;
        }

        public void setDomains(List<String> domains) {
            this.domains = domains == null ? new ArrayList<>() : domains;
        }
    }
}

