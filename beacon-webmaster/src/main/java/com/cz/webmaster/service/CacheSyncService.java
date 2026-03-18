package com.cz.webmaster.service;

/**
 * 缓存同步统一门面服务。
 *
 * <p>该接口用于向业务层提供统一的缓存同步入口，屏蔽不同缓存域之间在
 * key 构建方式、写入策略、删除策略和重建方式上的差异。</p>
 *
 * <p>调用方只需要说明“同步哪个缓存域、同步哪个对象”，
 * 具体如何路由到实际的缓存写删操作由实现类统一处理。</p>
 */
public interface CacheSyncService {

    /**
     * 同步新增或更新后的数据到缓存。
     *
     * <p>实现类会根据缓存域契约决定：</p>
     * <p>1. 如何构建逻辑 key；</p>
     * <p>2. 使用 Hash、Set 还是 String 结构写入；</p>
     * <p>3. 是直接覆盖写入，还是先删除后重建集合。</p>
     *
     * @param domain 缓存域编码，例如 {@code client_business}、{@code channel}
     * @param entityOrId 业务实体对象或主键标识；具体如何解析由域路由决定
     */
    void syncUpsert(String domain, Object entityOrId);

    /**
     * 同步删除或失效操作到缓存。
     *
     * <p>实现类会根据缓存域契约决定是否允许真正删除 key。
     * 例如某些镜像缓存域只允许覆盖写，不允许直接删除 key。</p>
     *
     * @param domain 缓存域编码
     * @param entityOrId 业务实体对象或主键标识；具体如何解析由域路由决定
     */
    void syncDelete(String domain, Object entityOrId);

    /**
     * 按域触发缓存重建。
     *
     * <p>支持传入单个缓存域编码或 {@code ALL}。
     * 当传入 {@code ALL} 时，表示重建当前被允许进入重建范围的域集合，
     * 并不等于系统中已注册的所有缓存域。</p>
     *
     * @param domain 缓存域编码或 {@code ALL}
     */
    void rebuildDomain(String domain);
}
