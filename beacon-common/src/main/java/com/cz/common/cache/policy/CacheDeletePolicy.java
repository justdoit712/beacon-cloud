package com.cz.common.cache.policy;

/**
 * 缓存域删除策略枚举。
 *
 * <p>用于定义某个缓存域在执行删除动作时，
 * 缓存层应当采用什么处理方式。</p>
 */
public enum CacheDeletePolicy {
    /**
     * 允许直接删除 Redis key。
     *
     * <p>适用于允许先删缓存、后续再重建或回填的缓存域。</p>
     */
    DELETE_KEY,

    /**
     * 不允许直接删除 key，只允许覆盖写或刷新。
     *
     * <p>适用于不希望出现缓存短暂空缺的高风险缓存域，
     * 例如余额域。</p>
     */
    OVERWRITE_ONLY
}
