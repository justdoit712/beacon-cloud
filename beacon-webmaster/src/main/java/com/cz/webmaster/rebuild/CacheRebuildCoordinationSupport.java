package com.cz.webmaster.rebuild;

import com.cz.webmaster.client.BeaconCacheWriteClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 缓存重建并发协调支持组件。
 *
 * <p>负责统一管理手工重建期间使用的域级锁与脏标记，
 * 供手工重建流程和运行时同步流程共享。</p>
 */
@Component
public class CacheRebuildCoordinationSupport {

    static final String REBUILD_LOCK_PREFIX = "cache:rebuild:";
    static final String REBUILD_DIRTY_PREFIX = "cache:rebuild:dirty:";
    static final long DEFAULT_LOCK_TTL_SECONDS = 300L;

    private final BeaconCacheWriteClient cacheWriteClient;

    public CacheRebuildCoordinationSupport(BeaconCacheWriteClient cacheWriteClient) {
        this.cacheWriteClient = cacheWriteClient;
    }

    /**
     * 尝试获取指定域的重建锁。
     *
     * @param domain 缓存域编码
     * @param ownerToken 本次锁持有者令牌
     * @return true 表示获取成功，false 表示已有重建进行中
     */
    public boolean tryAcquireRebuildLock(String domain, String ownerToken) {
        String safeDomain = normalizeDomain(domain);
        String safeToken = requireToken(ownerToken);
        Boolean acquired = cacheWriteClient.setIfAbsent(lockKey(safeDomain), safeToken, DEFAULT_LOCK_TTL_SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放指定域的重建锁。
     *
     * @param domain 缓存域编码
     * @param ownerToken 本次锁持有者令牌
     * @return true 表示当前锁由本调用释放，false 表示锁不存在或已被其他持有者替换
     */
    public boolean releaseRebuildLock(String domain, String ownerToken) {
        String safeDomain = normalizeDomain(domain);
        String safeToken = requireToken(ownerToken);
        Boolean deleted = cacheWriteClient.deleteIfValueMatches(lockKey(safeDomain), safeToken);
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * 判断指定域当前是否处于重建中。
     *
     * @param domain 缓存域编码
     * @return true 表示处于重建中
     */
    public boolean isRebuildRunning(String domain) {
        Object value = cacheWriteClient.get(lockKey(normalizeDomain(domain)));
        return value != null && StringUtils.hasText(String.valueOf(value));
    }

    /**
     * 为指定域记录脏标记。
     *
     * @param domain 缓存域编码
     * @param marker 脏标记内容
     */
    public void markDirty(String domain, String marker) {
        cacheWriteClient.set(dirtyKey(normalizeDomain(domain)), defaultMarker(marker));
    }

    /**
     * 原子消费指定域的脏标记。
     *
     * @param domain 缓存域编码
     * @return true 表示本次消费到了脏标记
     */
    public boolean consumeDirty(String domain) {
        Object value = cacheWriteClient.pop(dirtyKey(normalizeDomain(domain)));
        return value != null && StringUtils.hasText(String.valueOf(value));
    }

    String lockKey(String domain) {
        return REBUILD_LOCK_PREFIX + normalizeDomain(domain);
    }

    String dirtyKey(String domain) {
        return REBUILD_DIRTY_PREFIX + normalizeDomain(domain);
    }

    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            throw new IllegalArgumentException("domain must not be blank");
        }
        return domain.trim().toLowerCase(Locale.ROOT);
    }

    private String requireToken(String ownerToken) {
        if (!StringUtils.hasText(ownerToken)) {
            throw new IllegalArgumentException("ownerToken must not be blank");
        }
        return ownerToken.trim();
    }

    private String defaultMarker(String marker) {
        return StringUtils.hasText(marker) ? marker.trim() : String.valueOf(System.currentTimeMillis());
    }
}
