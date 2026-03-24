package com.cz.common.cache.meta;

/**
 * 缓存域主数据来源（真源）定义。
 * <p>
 * 用于明确某个域的数据最终以哪个存储为准，
 * 防止出现“多口径同时写入”导致的数据漂移。
 */
public enum CacheSourceOfTruth {
    /** 以 MySQL 为主口径，Redis 仅作为派生缓存。 */
    MYSQL,
    /** 以 Redis 为主口径（当前项目基础层默认不推荐余额使用该模式）。 */
    REDIS
}
