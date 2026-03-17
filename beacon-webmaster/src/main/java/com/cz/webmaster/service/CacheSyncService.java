package com.cz.webmaster.service;

/**
 * 缓存同步门面服务。
 * <p>
 * 第九步先提供可复用的统一骨架，后续 Runtime/Manual/Boot 三层都复用该接口。
 */
public interface CacheSyncService {

    /**
     * 同步“新增/更新”到缓存。
     *
     * @param domain     缓存域编码（见 CacheDomainRegistry）
     * @param entityOrId 实体对象或主键标识（由域路由决定解析方式）
     */
    void syncUpsert(String domain, Object entityOrId);

    /**
     * 同步“删除/失效”到缓存。
     *
     * @param domain     缓存域编码
     * @param entityOrId 实体对象或主键标识
     */
    void syncDelete(String domain, Object entityOrId);

    /**
     * 按域执行重建（骨架实现）。
     * <p>
     * 支持单域或 ALL 入口，ALL 当前表示“允许范围内的域集合”，
     * 并不等于注册表全部域。
     *
     * @param domain 缓存域编码或 ALL
     */
    void rebuildDomain(String domain);
}
