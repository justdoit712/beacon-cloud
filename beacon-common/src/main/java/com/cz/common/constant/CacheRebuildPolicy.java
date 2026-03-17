package com.cz.common.constant;

/**
 * 缓存域重建策略枚举。
 */
public enum CacheRebuildPolicy {
    /**
     * 允许完整重建。
     *
     * <p>表示该域可以参与手工重建，也可以在启动校准阶段按开关参与重建。</p>
     */
    FULL_REBUILD,

    /**
     * 允许完整重建，但默认跳过启动阶段重建。
     *
     * <p>适用于可以手工修复，但不适合在服务启动时默认重建的域。</p>
     */
    FULL_REBUILD_SKIP_BOOT
}
