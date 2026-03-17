package com.cz.cache.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 缓存 key 命名空间配置。
 *
 * <p>逻辑 key 示例：{@code client_balance:1}</p>
 * <p>物理 key 示例：{@code beacon:dev:beacon-cloud:cz:client_balance:1}</p>
 *
 * <p>当前规则保持简单：</p>
 * <p>1. 当 {@code enabled=false} 时，不启用命名空间改写；</p>
 * <p>2. 当 {@code enabled=true} 时，必须通过 {@code fullPrefix} 提供完整前缀。</p>
 */
@Component
@ConfigurationProperties(prefix = "cache.namespace")
public class CacheNamespaceProperties {

    /** 是否启用命名空间改写。 */
    private boolean enabled = true;

    /**
     * Redis 物理 key 使用的完整命名空间前缀。
     *
     * <p>示例：{@code beacon:dev:beacon-cloud:cz:}</p>
     */
    private String fullPrefix = "beacon:dev:beacon-cloud:cz:";

    /**
     * 返回是否启用命名空间改写。
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用命名空间改写。
     *
     * @param enabled 是否启用命名空间改写
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回完整命名空间前缀。
     *
     * @return 完整命名空间前缀
     */
    public String getFullPrefix() {
        return fullPrefix;
    }

    /**
     * 设置完整命名空间前缀。
     *
     * @param fullPrefix 完整命名空间前缀
     */
    public void setFullPrefix(String fullPrefix) {
        this.fullPrefix = fullPrefix;
    }

    /**
     * 解析最终命名空间前缀。
     *
     * @return 命名空间前缀；若未启用则返回空字符串
     * @throws IllegalArgumentException 当启用命名空间且 {@code fullPrefix} 为空白时抛出
     */
    public String resolvePrefix() {
        if (!enabled) {
            return "";
        }
        return ensureSuffixColon(requireText(fullPrefix, "cache.namespace.fullPrefix"));
    }

    /**
     * 确保前缀以冒号结尾。
     *
     * @param value 原始前缀
     * @return 规范化后的前缀
     */
    private static String ensureSuffixColon(String value) {
        return value.endsWith(":") ? value : value + ":";
    }

    /**
     * 校验文本配置非空白。
     *
     * @param value 配置值
     * @param name 配置项名称
     * @return 去除首尾空白后的文本
     */
    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
