package com.cz.common.constant;

/**
 * Redis 数据结构类型。
 * <p>
 * 该枚举用于描述“缓存域契约”对应的 Redis 存储模型，
 * 以便同步组件在写入、删除、重建时选择正确操作。
 */
public enum CacheRedisType {
    /** 字符串类型：适用于简单标记值，如 black:mobile -> "1"。 */
    STRING,
    /** 哈希类型：适用于对象字段映射，如 client_business。 */
    HASH,
    /** 集合类型：适用于无序去重集合，如 dirty_word、client_sign。 */
    SET,
    /** 有序集合类型：适用于按分值排序的集合场景。 */
    ZSET
}
