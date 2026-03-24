package com.cz.webmaster.config;

import org.springframework.stereotype.Component;

/**
 * 缓存命名空间一致性守卫组件。
 *
 * <p>该类本身不承载具体校验逻辑，真正的校验工作由
 * {@link CacheSyncProperties} 在配置初始化阶段完成。</p>
 *
 * <p>保留该组件的目的，是在配置层显式表达“业务侧同步命名空间”
 * 与“缓存侧物理命名空间”必须保持一致这一启动约束，避免该规则仅隐含在
 * 配置类内部而不易被发现。</p>
 */
@Component
public class CacheNamespaceConsistencyGuard {

    /**
     * 创建缓存命名空间一致性守卫组件。
     *
     * <p>通过依赖注入 {@link CacheSyncProperties}，确保其在 Spring 容器启动过程中
     * 被提前初始化并完成命名空间一致性校验。</p>
     *
     * @param cacheSyncProperties 缓存同步配置对象
     */
    public CacheNamespaceConsistencyGuard(CacheSyncProperties cacheSyncProperties) {
        // 当前类作为显式守卫组件存在。
        // 只要该依赖被成功注入，就意味着 CacheSyncProperties 已完成初始化与校验。
    }
}
