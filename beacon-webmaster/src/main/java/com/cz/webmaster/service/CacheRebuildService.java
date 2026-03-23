package com.cz.webmaster.service;

import com.cz.webmaster.dto.CacheRebuildReport;

/**
 * 缓存重建协调服务。
 *
 * <p>用于承接手工重建、启动校准等上层触发入口，统一协调缓存重建执行，
 * 并返回结构化重建报告。</p>
 */
public interface CacheRebuildService {

    /**
     * 按域触发缓存重建。
     *
     * <p>支持传入单个缓存域编码或 {@code ALL}。
     * 当传入 {@code ALL} 时，表示重建当前被允许进入重建范围的域集合，
     * 并不等于系统中已注册的所有缓存域。</p>
     *
     * @param domain 缓存域编码或 {@code ALL}
     * @return 结构化重建报告
     */
    CacheRebuildReport rebuildDomain(String domain);

    /**
     * 按域触发启动阶段缓存重建。
     *
     * <p>该入口用于启动校准场景，复用与手工重建相同的底层重建引擎，
     * 但是否允许执行由启动校准配置与启动校准域规则决定。</p>
     *
     * @param domain 缓存域编码
     * @return 结构化重建报告
     */
    CacheRebuildReport rebuildBootDomain(String domain);
}
