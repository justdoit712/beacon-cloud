package com.cz.webmaster.service.impl;

import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.service.CacheRebuildService;
import org.springframework.stereotype.Service;

/**
 * {@link CacheRebuildService} 的默认实现。
 *
 * <p>该类作为缓存重建协调层，对外暴露统一的重建入口，
 * 当前复用 {@link CacheSyncServiceImpl} 中已经实现的重建引擎能力，
 * 为控制层和后续启动校准等入口提供稳定的调用面。</p>
 */
@Service
public class CacheRebuildServiceImpl implements CacheRebuildService {

    private final CacheSyncServiceImpl cacheSyncService;

    /**
     * 创建缓存重建协调服务。
     *
     * @param cacheSyncService 缓存同步门面实现
     */
    public CacheRebuildServiceImpl(CacheSyncServiceImpl cacheSyncService) {
        this.cacheSyncService = cacheSyncService;
    }

    /**
     * 委托底层重建引擎执行缓存重建。
     *
     * @param domain 缓存域编码或 {@code ALL}
     * @return 结构化重建报告
     */
    @Override
    public CacheRebuildReport rebuildDomain(String domain) {
        return cacheSyncService.rebuildDomain(domain);
    }
}
