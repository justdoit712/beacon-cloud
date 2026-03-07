package com.cz.common.constant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个缓存业务域的统一契约定义。
 * <p>
 * 该类用于固化一个域（如 client_business、client_sign）在缓存中的核心规则，
 * 包括 key 模板、数据结构、主数据源、写删策略、重建策略、归属服务等。
 * 该对象是不可变对象，构造时会完成参数校验，确保契约本身合法。
 */
public final class CacheDomainContract {

    /** 域编码（唯一标识），如 client_business。 */
    private final String domainCode;
    /** 逻辑 key 模板列表（不包含命名空间前缀）。 */
    private final List<String> logicalKeyPatterns;
    /** Redis 结构类型。 */
    private final CacheRedisType redisType;
    /** 主数据来源（真源）。 */
    private final CacheSourceOfTruth sourceOfTruth;
    /** 写入策略标识（如 WRITE_THROUGH）。 */
    private final String writePolicy;
    /** 删除策略标识（如 DELETE_KEY）。 */
    private final String deletePolicy;
    /** 重建策略标识（如 FULL_REBUILD）。 */
    private final String rebuildPolicy;
    /** 该域的归属服务（用于治理和定位）。 */
    private final String ownerService;
    /** 是否允许在启动校准阶段自动参与重建。 */
    private final boolean bootRebuildEnabled;

    /**
     * 构造缓存域契约。
     *
     * @param domainCode         域编码
     * @param logicalKeyPatterns 逻辑 key 模板
     * @param redisType          Redis 数据结构类型
     * @param sourceOfTruth      主数据源
     * @param writePolicy        写入策略
     * @param deletePolicy       删除策略
     * @param rebuildPolicy      重建策略
     * @param ownerService       归属服务
     * @param bootRebuildEnabled 是否允许启动重建
     */
    public CacheDomainContract(String domainCode,
                               List<String> logicalKeyPatterns,
                               CacheRedisType redisType,
                               CacheSourceOfTruth sourceOfTruth,
                               String writePolicy,
                               String deletePolicy,
                               String rebuildPolicy,
                               String ownerService,
                               boolean bootRebuildEnabled) {
        this.domainCode = requireText(domainCode, "domainCode");
        this.logicalKeyPatterns = normalizePatterns(logicalKeyPatterns);
        this.redisType = requireNotNull(redisType, "redisType");
        this.sourceOfTruth = requireNotNull(sourceOfTruth, "sourceOfTruth");
        this.writePolicy = requireText(writePolicy, "writePolicy");
        this.deletePolicy = requireText(deletePolicy, "deletePolicy");
        this.rebuildPolicy = requireText(rebuildPolicy, "rebuildPolicy");
        this.ownerService = requireText(ownerService, "ownerService");
        this.bootRebuildEnabled = bootRebuildEnabled;
    }

    /** @return 域编码。 */
    public String getDomainCode() {
        return domainCode;
    }

    /** @return 逻辑 key 模板列表（只读）。 */
    public List<String> getLogicalKeyPatterns() {
        return logicalKeyPatterns;
    }

    /** @return Redis 数据结构类型。 */
    public CacheRedisType getRedisType() {
        return redisType;
    }

    /** @return 主数据来源。 */
    public CacheSourceOfTruth getSourceOfTruth() {
        return sourceOfTruth;
    }

    /** @return 写入策略标识。 */
    public String getWritePolicy() {
        return writePolicy;
    }

    /** @return 删除策略标识。 */
    public String getDeletePolicy() {
        return deletePolicy;
    }

    /** @return 重建策略标识。 */
    public String getRebuildPolicy() {
        return rebuildPolicy;
    }

    /** @return 归属服务名称。 */
    public String getOwnerService() {
        return ownerService;
    }

    /** @return 是否允许启动重建。 */
    public boolean isBootRebuildEnabled() {
        return bootRebuildEnabled;
    }

    /**
     * 规范化逻辑 key 模板列表。
     * <p>
     * 要求列表非空，每个模板非空白；返回不可变列表，防止外部修改。
     */
    private static List<String> normalizePatterns(List<String> logicalKeyPatterns) {
        if (logicalKeyPatterns == null || logicalKeyPatterns.isEmpty()) {
            throw new IllegalArgumentException("logicalKeyPatterns must not be empty");
        }
        List<String> result = new ArrayList<>(logicalKeyPatterns.size());
        for (String pattern : logicalKeyPatterns) {
            String normalized = requireText(pattern, "logicalKeyPattern");
            result.add(normalized);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 校验字符串字段必须非空白。
     *
     * @param value     字段值
     * @param fieldName 字段名（用于错误提示）
     * @return trim 后字符串
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 校验对象字段必须非 null。
     *
     * @param value     对象值
     * @param fieldName 字段名（用于错误提示）
     * @param <T>       对象类型
     * @return 原对象
     */
    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
