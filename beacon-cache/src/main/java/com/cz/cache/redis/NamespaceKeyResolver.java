package com.cz.cache.redis;

import com.cz.cache.security.CacheNamespaceProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 命名空间 Key 解析器。
 * <p>
 * 负责在“逻辑 Key”和“带命名空间前缀的物理 Key”之间做双向转换，
 * 用于保证调用方仍可按统一逻辑 Key 编码，而 Redis 实际落库使用物理 Key。
 * <p>
 * 设计原则：
 * <p>
 * 1. 调用方尽量只处理逻辑 Key（例如 client_balance:1001）。<br>
 * 2. 落库前统一转换为物理 Key（例如 beacon:dev:beacon-cloud:cz:client_balance:1001）。<br>
 * 3. 已经是物理 Key 的输入不重复加前缀，保证转换幂等。<br>
 * 4. 空字符串或空白字符串保持原样返回，避免误改上游参数语义。
 */
@Component
public class NamespaceKeyResolver {

    private final CacheNamespaceProperties namespaceProperties;

    public NamespaceKeyResolver(CacheNamespaceProperties namespaceProperties) {
        this.namespaceProperties = namespaceProperties;
    }

    /**
     * 将逻辑 Key 转换为物理 Key（加命名空间前缀）。
     * <p>
     * 行为说明：
     * <p>
     * 1. key 为空时直接原样返回。<br>
     * 2. 命名空间前缀为空（例如关闭命名空间）时直接返回原 key。<br>
     * 3. key 已包含前缀时不重复追加。<br>
     * 4. 其余情况按 prefix + key 拼接。
     *
     * @param key 逻辑 Key 或可能已是物理 Key
     * @return 物理 Key（或边界场景下原样返回）
     */
    public String toPhysicalKey(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        String prefix = namespaceProperties.resolvePrefix();
        if (!StringUtils.hasText(prefix) || key.startsWith(prefix)) {
            return key;
        }
        return prefix + key;
    }

    /**
     * 将物理 Key 还原为逻辑 Key（去命名空间前缀）。
     * <p>
     * 行为说明：
     * <p>
     * 1. key 为空时原样返回。<br>
     * 2. 命名空间前缀为空时原样返回。<br>
     * 3. 仅当 key 以当前前缀开头时才截断，避免误删中间片段。<br>
     * 4. 非当前命名空间的 key 会保持不变。
     *
     * @param key 物理 Key 或可能已是逻辑 Key
     * @return 逻辑 Key（或边界场景下原样返回）
     */
    public String toLogicalKey(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        String prefix = namespaceProperties.resolvePrefix();
        if (!StringUtils.hasText(prefix)) {
            return key;
        }
        return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
    }

    /**
     * 将逻辑 pattern 转换为物理 pattern。
     * <p>
     * 与 {@link #toPhysicalKey(String)} 使用相同规则，便于统一处理 keys/scan 查询。
     *
     * @param pattern 逻辑 pattern（例如 client_balance:*）
     * @return 物理 pattern（例如 beacon:dev:beacon-cloud:cz:client_balance:*）
     */
    public String toPhysicalPattern(String pattern) {
        return toPhysicalKey(pattern);
    }

    /**
     * 将物理 pattern 转换为逻辑 pattern。
     *
     * @param pattern 物理 pattern
     * @return 逻辑 pattern
     */
    public String toLogicalPattern(String pattern) {
        return toLogicalKey(pattern);
    }

    /**
     * 批量将物理 Key 集合转换为逻辑 Key 集合。
     * <p>
     * 返回 {@link LinkedHashSet}，用于尽量保持扫描结果的插入顺序并自动去重。
     *
     * @param physicalKeys 物理 Key 集合
     * @return 逻辑 Key 集合；若输入为空，返回空集合（非 null）
     */
    public Set<String> toLogicalKeys(Set<String> physicalKeys) {
        Set<String> logicalKeys = new LinkedHashSet<>();
        if (physicalKeys == null || physicalKeys.isEmpty()) {
            return logicalKeys;
        }
        for (String key : physicalKeys) {
            logicalKeys.add(toLogicalKey(key));
        }
        return logicalKeys;
    }
}
