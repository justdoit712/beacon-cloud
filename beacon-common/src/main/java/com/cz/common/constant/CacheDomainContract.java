package com.cz.common.constant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个缓存域的契约定义。
 *
 * <p>用于描述某个缓存域在系统中的固定规则，包括：</p>
 * <p>1. 域编码；</p>
 * <p>2. 逻辑 key 模板；</p>
 * <p>3. Redis 数据结构类型；</p>
 * <p>4. 真源类型；</p>
 * <p>5. 写入、删除、重建策略；</p>
 * <p>6. 归属服务；</p>
 * <p>7. 是否允许在启动阶段参与重建。</p>
 *
 * <p>该对象为不可变对象，构造完成后不再修改。</p>
 */
public final class CacheDomainContract {

    /** 域编码，作为缓存域的唯一标识。 */
    private final String domainCode;
    /** 逻辑 key 模板列表，不包含命名空间前缀。 */
    private final List<String> logicalKeyPatterns;
    /** Redis 数据结构类型。 */
    private final CacheRedisType redisType;
    /** 真源类型，表示该域最终以哪个存储为准。 */
    private final CacheSourceOfTruth sourceOfTruth;
    /** 写入策略。 */
    private final CacheWritePolicy writePolicy;
    /** 删除策略。 */
    private final CacheDeletePolicy deletePolicy;
    /** 重建策略。 */
    private final CacheRebuildPolicy rebuildPolicy;
    /** 该缓存域归属的服务名称。 */
    private final String ownerService;
    /** 是否允许在启动校准阶段参与自动重建。 */
    private final boolean bootRebuildEnabled;

    /**
     * 构造缓存域契约。
     *
     * @param domainCode 域编码
     * @param logicalKeyPatterns 逻辑 key 模板列表
     * @param redisType Redis 数据结构类型
     * @param sourceOfTruth 真源类型
     * @param writePolicy 写入策略
     * @param deletePolicy 删除策略
     * @param rebuildPolicy 重建策略
     * @param ownerService 归属服务名称
     * @param bootRebuildEnabled 是否允许启动阶段自动重建
     */
    public CacheDomainContract(String domainCode,
                               List<String> logicalKeyPatterns,
                               CacheRedisType redisType,
                               CacheSourceOfTruth sourceOfTruth,
                               CacheWritePolicy writePolicy,
                               CacheDeletePolicy deletePolicy,
                               CacheRebuildPolicy rebuildPolicy,
                               String ownerService,
                               boolean bootRebuildEnabled) {
        this.domainCode = requireText(domainCode, "domainCode");
        this.logicalKeyPatterns = normalizePatterns(logicalKeyPatterns);
        this.redisType = requireNotNull(redisType, "redisType");
        this.sourceOfTruth = requireNotNull(sourceOfTruth, "sourceOfTruth");
        this.writePolicy = requireNotNull(writePolicy, "writePolicy");
        this.deletePolicy = requireNotNull(deletePolicy, "deletePolicy");
        this.rebuildPolicy = requireNotNull(rebuildPolicy, "rebuildPolicy");
        this.ownerService = requireText(ownerService, "ownerService");
        this.bootRebuildEnabled = bootRebuildEnabled;
    }

    /**
     * 返回域编码。
     *
     * @return 域编码
     */
    public String getDomainCode() {
        return domainCode;
    }

    /**
     * 返回逻辑 key 模板列表。
     *
     * @return 逻辑 key 模板列表
     */
    public List<String> getLogicalKeyPatterns() {
        return logicalKeyPatterns;
    }

    /**
     * 返回 Redis 数据结构类型。
     *
     * @return Redis 数据结构类型
     */
    public CacheRedisType getRedisType() {
        return redisType;
    }

    /**
     * 返回真源类型。
     *
     * @return 真源类型
     */
    public CacheSourceOfTruth getSourceOfTruth() {
        return sourceOfTruth;
    }

    /**
     * 返回写入策略。
     *
     * @return 写入策略
     */
    public CacheWritePolicy getWritePolicy() {
        return writePolicy;
    }

    /**
     * 返回删除策略。
     *
     * @return 删除策略
     */
    public CacheDeletePolicy getDeletePolicy() {
        return deletePolicy;
    }

    /**
     * 返回重建策略。
     *
     * @return 重建策略
     */
    public CacheRebuildPolicy getRebuildPolicy() {
        return rebuildPolicy;
    }

    /**
     * 返回归属服务名称。
     *
     * @return 归属服务名称
     */
    public String getOwnerService() {
        return ownerService;
    }

    /**
     * 返回是否允许启动阶段自动重建。
     *
     * @return true 表示允许，false 表示不允许
     */
    public boolean isBootRebuildEnabled() {
        return bootRebuildEnabled;
    }

    /**
     * 规范化逻辑 key 模板列表。
     *
     * @param logicalKeyPatterns 原始逻辑 key 模板列表
     * @return 规范化后的只读列表
     */
    private static List<String> normalizePatterns(List<String> logicalKeyPatterns) {
        if (logicalKeyPatterns == null || logicalKeyPatterns.isEmpty()) {
            throw new IllegalArgumentException("logicalKeyPatterns must not be empty");
        }
        List<String> result = new ArrayList<>(logicalKeyPatterns.size());
        for (String pattern : logicalKeyPatterns) {
            result.add(requireText(pattern, "logicalKeyPattern"));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 校验文本字段非空白。
     *
     * @param value 字段值
     * @param fieldName 字段名
     * @return 去除首尾空白后的文本
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 校验对象字段不为 {@code null}。
     *
     * @param value 字段值
     * @param fieldName 字段名
     * @param <T> 对象类型
     * @return 原始对象
     */
    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
