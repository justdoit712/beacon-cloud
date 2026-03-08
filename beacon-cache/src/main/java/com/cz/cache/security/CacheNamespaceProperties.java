package com.cz.cache.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 缓存 Key 命名空间配置。
 * <p>
 * 逻辑 Key 示例（业务侧使用）：
 * client_balance:1
 * <p>
 * 物理 Key 示例（Redis 实际存储）：
 * beacon:{env}:{project}:{owner}:client_balance:1
 * <p>
 * 前缀解析优先级：
 * <p>
 * 1. enabled=false：不启用命名空间，返回空前缀。<br>
 * 2. fullPrefix 有值：直接使用 fullPrefix（自动补末尾冒号）。<br>
 * 3. 否则使用 env/project/owner 三段拼接固定格式前缀。
 */
@Component
@ConfigurationProperties(prefix = "cache.namespace")
public class CacheNamespaceProperties {

    /**
     * 是否启用命名空间改写。
     * <p>
     * true：逻辑 Key 会被改写为带前缀物理 Key。<br>
     * false：不改写，逻辑 Key 直接作为 Redis Key 使用。
     */
    private boolean enabled = true;

    /**
     * 命名空间中的环境段。
     * <p>
     * 示例：dev / test / prod。
     */
    private String env = "dev";

    /**
     * 命名空间中的项目段。
     * <p>
     * 建议使用稳定项目标识，避免随模块名变化导致前缀漂移。
     */
    private String project = "beacon-cloud";

    /**
     * 命名空间中的负责人段。
     * <p>
     * 当前按你的约束固定为 cz。
     */
    private String owner = "cz";

    /**
     * 可选的完整前缀覆盖配置。
     * <p>
     * 配置后将忽略 env/project/owner 三段拼装规则。
     * <p>
     * 示例：beacon:dev:beacon-cloud:cz:
     */
    private String fullPrefix;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getFullPrefix() {
        return fullPrefix;
    }

    public void setFullPrefix(String fullPrefix) {
        this.fullPrefix = fullPrefix;
    }

    /**
     * 解析最终命名空间前缀。
     *
     * @return 命名空间前缀；若 disabled 返回空字符串
     * @throws IllegalArgumentException 当 enabled=true 且必须字段为空白时抛出
     */
    public String resolvePrefix() {
        if (!enabled) {
            return "";
        }
        if (StringUtils.hasText(fullPrefix)) {
            return ensureSuffixColon(fullPrefix.trim());
        }
        String envValue = requireText(env, "cache.namespace.env");
        String projectValue = requireText(project, "cache.namespace.project");
        String ownerValue = requireText(owner, "cache.namespace.owner");
        return "beacon:" + envValue + ":" + projectValue + ":" + ownerValue + ":";
    }

    /**
     * 确保前缀以冒号结尾，避免调用方重复处理分隔符。
     *
     * @param value 输入前缀
     * @return 统一格式化后的前缀
     */
    private static String ensureSuffixColon(String value) {
        return value.endsWith(":") ? value : value + ":";
    }

    /**
     * 校验配置值非空白并返回去首尾空格后的结果。
     *
     * @param value 配置值
     * @param name  配置项名称（用于异常提示）
     * @return 规范化后的配置值
     */
    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
