package com.cz.common.cache.policy;

/**
 * 缓存域写入策略枚举。
 */
public enum CacheWritePolicy {
    /**
     * 写穿策略。
     *
     * <p>业务数据写入成功后，直接把最新值覆盖写入缓存。</p>
     */
    WRITE_THROUGH,

    /**
     * 删后重建策略。
     *
     * <p>先删除旧 key，再基于真源重建整组缓存内容。</p>
     * <p>适用于集合型域，尤其是成员可能发生增删变化的场景。</p>
     */
    DELETE_AND_REBUILD,

    /**
     * MySQL 原子更新后刷新缓存策略。
     *
     * <p>适用于余额这类高风险域：</p>
     * <p>先以 MySQL 原子更新为准，提交成功后再刷新 Redis 镜像。</p>
     */
    MYSQL_ATOMIC_UPDATE_THEN_REFRESH
}
