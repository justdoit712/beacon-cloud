package com.cz.webmaster.service.impl;

import com.cz.common.cache.meta.CacheDomainContract;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.common.cache.policy.CacheDeletePolicy;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import com.cz.webmaster.client.BeaconCacheWriteClient;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.dto.CacheDeleteResultDTO;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.rebuild.CacheRebuildCoordinationSupport;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import com.cz.webmaster.rebuild.DomainRebuildLoaderRegistry;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheKeyBuilder;
import com.cz.webmaster.support.CacheSyncLogHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@link CacheSyncService} 的默认实现。
 *
 * <p>该类负责把上层发起的缓存同步请求，统一路由为实际的缓存写入、
 * 删除或重建动作。</p>
 *
 * <p>主要职责包括：</p>
 * <p>1. 根据 {@link CacheDomainRegistry} 解析缓存域契约；</p>
 * <p>2. 使用 {@link CacheKeyBuilder} 统一构建逻辑 key；</p>
 * <p>3. 通过 {@link BeaconCacheWriteClient} 执行实际的缓存写删；</p>
 * <p>4. 通过 {@link CacheSyncLogHelper} 输出统一格式的同步日志；</p>
 * <p>5. 在不同同步入口之间复用同一套路由规则。</p>
 */
@Service
public class CacheSyncServiceImpl implements CacheSyncService {

    private static final Logger log = LoggerFactory.getLogger(CacheSyncServiceImpl.class);
    private static final String DOMAIN_ALL = "ALL";
    private static final String REBUILD_TRIGGER_MANUAL = "MANUAL";
    private static final String REBUILD_TRIGGER_BOOT = "BOOT";
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_KEY_UPPER = "TraceId";
    private static final String UNKNOWN = "-";
    private static final int DEFAULT_SCAN_COUNT = 1000;

    private final CacheSyncProperties cacheSyncProperties;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final BeaconCacheWriteClient cacheWriteClient;
    private final ObjectMapper objectMapper;
    private final DomainRebuildLoaderRegistry domainRebuildLoaderRegistry;
    private final CacheRebuildCoordinationSupport cacheRebuildCoordinationSupport;

    /**
     * 构造缓存同步门面实现。
     *
     * <p>该构造方法用于完整装配缓存同步所需依赖，
     * 包括重建加载器注册表与并发协调组件。</p>
     *
     * @param cacheSyncProperties 缓存同步配置
     * @param cacheKeyBuilder 逻辑 key 构建器
     * @param cacheWriteClient 缓存写删客户端
     * @param objectMapper 对象转换器
     * @param domainRebuildLoaderRegistry 域级重建加载器注册表
     * @param cacheRebuildCoordinationSupport 缓存重建并发协调组件；允许为 {@code null}
     */
    @Autowired
    public CacheSyncServiceImpl(CacheSyncProperties cacheSyncProperties,
                                CacheKeyBuilder cacheKeyBuilder,
                                BeaconCacheWriteClient cacheWriteClient,
                                ObjectMapper objectMapper,
                                DomainRebuildLoaderRegistry domainRebuildLoaderRegistry,
                                CacheRebuildCoordinationSupport cacheRebuildCoordinationSupport) {
        this.cacheSyncProperties = cacheSyncProperties;
        this.cacheKeyBuilder = cacheKeyBuilder;
        this.cacheWriteClient = cacheWriteClient;
        this.objectMapper = objectMapper;
        this.domainRebuildLoaderRegistry = domainRebuildLoaderRegistry;
        this.cacheRebuildCoordinationSupport = cacheRebuildCoordinationSupport;
    }

    /**
     * 构造缓存同步门面实现。
     *
     * <p>该构造方法用于仅指定重建加载器注册表的场景。
     * 当未显式传入并发协调组件时，手工重建相关并发避让能力保持关闭。</p>
     *
     * @param cacheSyncProperties 缓存同步配置
     * @param cacheKeyBuilder 逻辑 key 构建器
     * @param cacheWriteClient 缓存写删客户端
     * @param objectMapper 对象转换器
     * @param domainRebuildLoaderRegistry 域级重建加载器注册表
     */
    public CacheSyncServiceImpl(CacheSyncProperties cacheSyncProperties,
                                CacheKeyBuilder cacheKeyBuilder,
                                BeaconCacheWriteClient cacheWriteClient,
                                ObjectMapper objectMapper,
                                DomainRebuildLoaderRegistry domainRebuildLoaderRegistry) {
        this(
                cacheSyncProperties,
                cacheKeyBuilder,
                cacheWriteClient,
                objectMapper,
                domainRebuildLoaderRegistry,
                null
        );
    }

    /**
     * 根据缓存域路由规则执行新增或更新同步。
     *
     * <p>该方法会依次完成运行时开关校验、域契约解析、key 构建、
     * 缓存写入和日志记录。</p>
     *
     * @param domain 缓存域编码
     * @param entityOrId 业务实体对象或主键标识
     */
    @Override
    public void syncUpsert(String domain, Object entityOrId) {
        long startAt = System.currentTimeMillis();
        String normalizedDomain = normalizeDomain(domain);
        String entityId = resolveEntityId(entityOrId);
        String key = "-";
        String operation = "syncUpsert";
        try {
            if (!cacheSyncProperties.isEnabled() || !cacheSyncProperties.getRuntime().isEnabled()) {
                CacheSyncLogHelper.info(log, normalizedDomain, entityId, key, operation + ".skip", costMs(startAt));
                return;
            }

            CacheDomainContract contract = requireDomainContract(normalizedDomain);
            key = buildKey(contract.getDomainCode(), entityOrId);

            doUpsert(contract.getDomainCode(), key, entityOrId);
            CacheSyncLogHelper.info(log, contract.getDomainCode(), entityId, key, operation, costMs(startAt));
        } catch (ApiException ex) {
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    entityId,
                    key,
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_WRITE_FAIL,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        } catch (Exception ex) {
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    entityId,
                    key,
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_WRITE_FAIL,
                    ex.getMessage(),
                    ex
            );
            throw new ApiException(ExceptionEnums.CACHE_SYNC_WRITE_FAIL);
        }
    }

    /**
     * 根据缓存域路由规则执行删除或失效同步。
     *
     * <p>对于删除策略为“只允许覆盖写”的缓存域，该方法会显式跳过删 key 操作，
     * 并记录跳过日志。</p>
     *
     * @param domain 缓存域编码
     * @param entityOrId 业务实体对象或主键标识
     */
    @Override
    public void syncDelete(String domain, Object entityOrId) {
        long startAt = System.currentTimeMillis();
        String normalizedDomain = normalizeDomain(domain);
        String entityId = resolveEntityId(entityOrId);
        String key = "-";
        String operation = "syncDelete";
        try {
            if (!cacheSyncProperties.isEnabled() || !cacheSyncProperties.getRuntime().isEnabled()) {
                CacheSyncLogHelper.info(log, normalizedDomain, entityId, key, operation + ".skip", costMs(startAt));
                return;
            }

            CacheDomainContract contract = requireDomainContract(normalizedDomain);
            key = buildKey(contract.getDomainCode(), entityOrId);

            // 对于只允许覆盖写的镜像缓存域，删除动作会被显式跳过。
            if (contract.getDeletePolicy() == CacheDeletePolicy.OVERWRITE_ONLY) {
                CacheSyncLogHelper.info(log, contract.getDomainCode(), entityId, key, operation + ".skipOverwriteOnly", costMs(startAt));
                return;
            }

            cacheWriteClient.delete(key);
            CacheSyncLogHelper.info(log, contract.getDomainCode(), entityId, key, operation, costMs(startAt));
        } catch (ApiException ex) {
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    entityId,
                    key,
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_DELETE_FAIL,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        } catch (Exception ex) {
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    entityId,
                    key,
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_DELETE_FAIL,
                    ex.getMessage(),
                    ex
            );
            throw new ApiException(ExceptionEnums.CACHE_SYNC_DELETE_FAIL);
        }
    }

    /**
     * 根据缓存域触发重建入口。
     *
     * <p>当传入 {@code ALL} 时，会遍历当前允许重建的域集合；
     * 当传入单个域时，会校验该域是否允许进入重建范围。</p>
     *
     * @param domain 缓存域编码或 {@code ALL}
     */
    @Override
    public CacheRebuildReport rebuildDomain(String domain) {
        long startAt = System.currentTimeMillis();
        String operation = "rebuildDomain";
        String normalizedDomain = normalizeDomain(domain);
        try {
            if (!cacheSyncProperties.isEnabled() || !cacheSyncProperties.getManual().isEnabled()) {
                CacheSyncLogHelper.info(log, normalizedDomain, "-", "-", operation + ".skip", costMs(startAt));
                return buildSkippedRebuildReport(normalizedDomain, startAt, "manual rebuild disabled");
            }

            if (DOMAIN_ALL.equalsIgnoreCase(normalizedDomain)) {
                List<CacheRebuildReport> childReports = new ArrayList<>();
                for (String allowedDomain : resolveCurrentRegisteredManualRebuildDomains()) {
                    childReports.add(rebuildSingleDomain(allowedDomain));
                }
                CacheSyncLogHelper.info(log, DOMAIN_ALL, "-", "-", operation, costMs(startAt));
                return buildAggregateRebuildReport(DOMAIN_ALL, startAt, childReports);
            }

            if (!CacheDomainRegistry.isCurrentMainlineDomain(normalizedDomain)) {
                throw new ApiException("unsupported manual rebuild domain: " + normalizedDomain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }
            if (!CacheDomainRegistry.isCurrentManualRebuildDomain(normalizedDomain)) {
                throw new ApiException("manual rebuild domain not allowed yet: " + normalizedDomain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }
            if (!domainRebuildLoaderRegistry.contains(normalizedDomain)) {
                throw new ApiException("manual rebuild loader not registered: " + normalizedDomain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }

            CacheRebuildReport report = rebuildSingleDomain(normalizedDomain);
            CacheSyncLogHelper.info(log, normalizedDomain, "-", "-", operation, costMs(startAt));
            return report;
        } catch (ApiException ex) {
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    "-",
                    "-",
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_CONFIG_INVALID,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        } catch (Exception ex) {
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    "-",
                    "-",
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_CONFIG_INVALID,
                    ex.getMessage(),
                    ex
            );
            throw new ApiException(ExceptionEnums.CACHE_SYNC_CONFIG_INVALID);
        }
    }

    /**
     * 根据缓存域触发启动阶段重建入口。
     *
     * <p>该入口复用与手工重建相同的底层重建引擎与域级锁，
     * 但是否允许执行由启动校准开关和启动校准域规则决定。</p>
     *
     * @param domain 缓存域编码
     * @return 结构化重建报告
     */
    public CacheRebuildReport rebuildBootDomain(String domain) {
        long startAt = System.currentTimeMillis();
        String operation = "rebuildBootDomain";
        String normalizedDomain = normalizeDomain(domain);
        try {
            if (!cacheSyncProperties.isEnabled()
                    || cacheSyncProperties.getBoot() == null
                    || !cacheSyncProperties.getBoot().isEnabled()) {
                CacheSyncLogHelper.info(log, normalizedDomain, "-", "-", operation + ".skip", costMs(startAt));
                return buildBootSkippedRebuildReport(normalizedDomain, startAt, "boot reconcile disabled");
            }

            if (!CacheDomainRegistry.isCurrentMainlineDomain(normalizedDomain)) {
                throw new ApiException("unsupported boot reconcile domain: " + normalizedDomain,
                        ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }
            if (!CacheDomainRegistry.require(normalizedDomain).isBootRebuildEnabled()) {
                throw new ApiException("boot reconcile domain not allowed yet: " + normalizedDomain,
                        ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }
            if (!domainRebuildLoaderRegistry.contains(normalizedDomain)) {
                throw new ApiException("boot reconcile loader not registered: " + normalizedDomain,
                        ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }

            CacheRebuildReport report = rebuildSingleDomain(normalizedDomain);
            return adaptBootReport(report, operation, startAt);
        } catch (ApiException ex) {
            ApiException bootException = adaptBootException(ex);
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    "-",
                    "-",
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_CONFIG_INVALID,
                    bootException.getMessage(),
                    bootException
            );
            throw bootException;
        } catch (Exception ex) {
            CacheSyncLogHelper.error(
                    log,
                    normalizedDomain,
                    "-",
                    "-",
                    operation,
                    costMs(startAt),
                    ExceptionEnums.CACHE_SYNC_CONFIG_INVALID,
                    ex.getMessage(),
                    ex
            );
            throw new ApiException(ExceptionEnums.CACHE_SYNC_CONFIG_INVALID);
        }
    }

    /**
     * 执行单个缓存域的手工重建流程。
     *
     * <p>该方法负责单域重建的完整协调与执行，包括：</p>
     * <p>1. 获取域级重建锁，避免同域并发进入；</p>
     * <p>2. 根据域契约与重建加载器执行一次完整重建；</p>
     * <p>3. 若存在脏标记，则立即触发补跑；</p>
     * <p>4. 汇总结构化报告并释放域级锁。</p>
     *
     * @param domain 缓存域编码
     * @return 单域重建报告
     */
    private CacheRebuildReport rebuildSingleDomain(String domain) {
        long startAt = System.currentTimeMillis();
        String lockToken = UUID.randomUUID().toString();
        CacheDomainContract contract = requireDomainContract(domain);
        DomainRebuildLoader loader = requireRebuildLoader(domain);
        // 单域手工重建先尝试获取域级锁，避免同域重复进入重建流程。
        if (cacheRebuildCoordinationSupport != null
                && !cacheRebuildCoordinationSupport.tryAcquireRebuildLock(domain, lockToken)) {
            throw new ApiException("manual rebuild domain busy: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }

        CacheRebuildReport report = initDomainRebuildReport(domain, startAt);
        try {
            executeRebuildPass(contract, loader, report, "initial");

            boolean dirtyReplay = false;
            while (cacheRebuildCoordinationSupport != null && cacheRebuildCoordinationSupport.consumeDirty(domain)) {
                dirtyReplay = true;
                executeRebuildPass(contract, loader, report, "dirtyReplay");
            }
            report.setDirtyReplay(dirtyReplay);
            applyFinalReportState(report);
            report.setEndAt(System.currentTimeMillis());
            return report;
        } catch (Exception ex) {
            markEngineFailure(report, ex);
            report.setEndAt(System.currentTimeMillis());
            return report;
        } finally {
            if (cacheRebuildCoordinationSupport != null) {
                cacheRebuildCoordinationSupport.releaseRebuildLock(domain, lockToken);
            }
        }
    }

    /**
     * 根据缓存域路由到对应的写入实现。
     *
     * @param domain 缓存域编码
     * @param key 逻辑 key
     * @param entityOrId 业务实体对象或主键标识
     */
    private void doUpsert(String domain, String key, Object entityOrId) {
        if (CacheDomainRegistry.isCurrentMainlineDomain(domain)) {
            doCurrentMainlineUpsert(domain, key, entityOrId);
            return;
        }
        if (CacheDomainRegistry.isCurrentLegacyCompatibleDomain(domain)) {
            doLegacyCompatibleUpsert(domain, key, entityOrId);
            return;
        }
        throw new ApiException("unsupported upsert domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 执行当前主线域的写入逻辑。
     *
     * @param domain 缓存域编码
     * @param key 逻辑 key
     * @param entityOrId 业务实体对象或主键标识
     */
    private void doCurrentMainlineUpsert(String domain, String key, Object entityOrId) {
        if (CacheDomainRegistry.CLIENT_BUSINESS.equals(domain)) {
            cacheWriteClient.hmset(key, resolveClientBusinessPayload(entityOrId));
            return;
        }
        if (CacheDomainRegistry.CLIENT_BALANCE.equals(domain)) {
            cacheWriteClient.hmset(key, resolveClientBalancePayload(entityOrId));
            return;
        }
        if (CacheDomainRegistry.CHANNEL.equals(domain)) {
            cacheWriteClient.hmset(key, resolveChannelPayload(entityOrId));
            return;
        }
        if (CacheDomainRegistry.CLIENT_CHANNEL.equals(domain)) {
            requireSnapshotPayload(entityOrId);
            rebuildSetDomain(key, entityOrId, true);
            return;
        }
        throw new ApiException("unsupported current mainline upsert domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 执行兼容保留域的写入逻辑。
     *
     * @param domain 缓存域编码
     * @param key 逻辑 key
     * @param entityOrId 业务实体对象或主键标识
     */
    private void doLegacyCompatibleUpsert(String domain, String key, Object entityOrId) {
        if (isLegacySetDomain(domain)) {
            rebuildSetDomain(key, entityOrId, isLegacyObjectSetDomain(domain));
            return;
        }
        if (CacheDomainRegistry.BLACK.equals(domain) || CacheDomainRegistry.TRANSFER.equals(domain)) {
            cacheWriteClient.set(key, resolveStringValue(domain, entityOrId));
            return;
        }
        throw new ApiException("unsupported legacy compatible upsert domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 判断指定域是否属于兼容保留的 Set 型缓存域。
     *
     * @param domain 缓存域编码
     * @return true 表示属于兼容 Set 域
     */
    private boolean isLegacySetDomain(String domain) {
        return CacheDomainRegistry.CLIENT_SIGN.equals(domain)
                || CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain)
                || CacheDomainRegistry.DIRTY_WORD.equals(domain);
    }

    /**
     * 判断兼容 Set 型缓存域是否使用对象成员。
     *
     * @param domain 缓存域编码
     * @return true 表示使用对象成员，false 表示使用字符串成员
     */
    private boolean isLegacyObjectSetDomain(String domain) {
        return CacheDomainRegistry.CLIENT_SIGN.equals(domain)
                || CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain);
    }

    /**
     * 按“先删后重建”的方式重建 Set 型缓存。
     *
     * @param key 逻辑 key
     * @param entityOrId 业务实体对象或主键标识
     * @param objectSetDomain true 表示对象 Set，false 表示字符串 Set
     */
    private void rebuildSetDomain(String key, Object entityOrId, boolean objectSetDomain) {
        cacheWriteClient.delete(key);
        if (objectSetDomain) {
            Map<String, Object>[] mapMembers = resolveSetMapMembers(entityOrId);
            if (mapMembers.length > 0) {
                cacheWriteClient.sadd(key, mapMembers);
                return;
            }
        }
        String[] members = resolveSetMembers(entityOrId);
        if (members.length > 0) {
            cacheWriteClient.saddStr(key, members);
        }
    }

    /**
     * 根据缓存域构建逻辑 key。
     *
     * @param domain 缓存域编码
     * @param entityOrId 业务实体对象或主键标识
     * @return 逻辑 key
     */
    private String buildKey(String domain, Object entityOrId) {
        if (CacheDomainRegistry.isCurrentMainlineDomain(domain)) {
            return buildCurrentMainlineKey(domain, entityOrId);
        }
        if (CacheDomainRegistry.isCurrentLegacyCompatibleDomain(domain)) {
            return buildLegacyCompatibleKey(domain, entityOrId);
        }
        throw new ApiException("unsupported cache domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 构建当前主线域的逻辑 key。
     *
     * @param domain 缓存域编码
     * @param entityOrId 业务实体对象或主键标识
     * @return 主线域逻辑 key
     */
    private String buildCurrentMainlineKey(String domain, Object entityOrId) {
        if (CacheDomainRegistry.CLIENT_BUSINESS.equals(domain)) {
            return cacheKeyBuilder.clientBusinessByApiKey(readText(entityOrId, "apiKey", "apikey"));
        }
        if (CacheDomainRegistry.CLIENT_BALANCE.equals(domain)) {
            return cacheKeyBuilder.clientBalanceByClientId(readLong(entityOrId, "clientId"));
        }
        if (CacheDomainRegistry.CLIENT_CHANNEL.equals(domain)) {
            return cacheKeyBuilder.clientChannelByClientId(readLong(entityOrId, "clientId"));
        }
        if (CacheDomainRegistry.CHANNEL.equals(domain)) {
            return cacheKeyBuilder.channelById(readLong(entityOrId, "id", "channelId"));
        }
        throw new ApiException("unsupported current mainline key domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 构建兼容保留域的逻辑 key。
     *
     * @param domain 缓存域编码
     * @param entityOrId 业务实体对象或主键标识
     * @return 兼容域逻辑 key
     */
    private String buildLegacyCompatibleKey(String domain, Object entityOrId) {
        if (CacheDomainRegistry.CLIENT_SIGN.equals(domain)) {
            return cacheKeyBuilder.clientSignByClientId(readLong(entityOrId, "clientId", "id"));
        }
        if (CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain)) {
            return cacheKeyBuilder.clientTemplateBySignId(readLong(entityOrId, "signId", "id"));
        }
        if (CacheDomainRegistry.BLACK.equals(domain)) {
            Long clientId = tryReadLong(entityOrId, "clientId", "id");
            String mobile = readText(entityOrId, "mobile");
            return clientId == null ? cacheKeyBuilder.blackGlobal(mobile) : cacheKeyBuilder.blackClient(clientId, mobile);
        }
        if (CacheDomainRegistry.DIRTY_WORD.equals(domain)) {
            return cacheKeyBuilder.dirtyWord();
        }
        if (CacheDomainRegistry.TRANSFER.equals(domain)) {
            return cacheKeyBuilder.transfer(readText(entityOrId, "mobile"));
        }
        throw new ApiException("unsupported legacy compatible key domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 强校验获取指定缓存域契约。
     *
     * @param domain 缓存域编码
     * @return 缓存域契约
     */
    private CacheDomainContract requireDomainContract(String domain) {
        try {
            return CacheDomainRegistry.require(domain);
        } catch (Exception ex) {
            throw new ApiException("unsupported cache domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
    }

    /**
     * 强校验获取指定缓存域的重建加载器。
     *
     * @param domain 缓存域编码
     * @return 域级重建加载器
     */
    private DomainRebuildLoader requireRebuildLoader(String domain) {
        DomainRebuildLoader loader = domainRebuildLoaderRegistry.get(domain);
        if (loader == null) {
            throw new ApiException("manual rebuild loader not registered: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return loader;
    }

    /**
     * 规范化缓存域编码。
     *
     * @param domain 原始缓存域编码
     * @return 规范化后的域编码
     */
    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            throw new ApiException("domain must not be blank", ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return domain.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 解析用于日志输出的实体标识。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return 可用于日志输出的实体标识
     */
    private String resolveEntityId(Object entityOrId) {
        if (entityOrId == null) {
            return "-";
        }
        if (entityOrId instanceof Number || entityOrId instanceof String) {
            String value = entityOrId.toString().trim();
            return value.isEmpty() ? "-" : value;
        }
        Map<String, Object> map = toMap(entityOrId);
        Object id = firstNonNull(map, "id", "clientId", "signId", "mobile", "apiKey", "apikey");
        return id == null ? "-" : String.valueOf(id);
    }

    /**
     * 解析通用 Hash 写入载荷。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return Hash 写入载荷
     */
    private Map<String, Object> resolveHashPayload(Object entityOrId) {
        Map<String, Object> map = toMap(entityOrId);
        return map.isEmpty() ? new LinkedHashMap<>() : map;
    }

    /**
     * 解析 {@code client_business} 域写入载荷。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return 客户业务 Hash 写入载荷
     */
    private Map<String, Object> resolveClientBusinessPayload(Object entityOrId) {
        Map<String, Object> payload = resolveHashPayload(entityOrId);
        readText(payload, "apiKey", "apikey");
        return payload;
    }

    /**
     * 解析 {@code client_balance} 域写入载荷。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return 客户余额 Hash 写入载荷
     */
    private Map<String, Object> resolveClientBalancePayload(Object entityOrId) {
        Map<String, Object> source = resolveHashPayload(entityOrId);
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("clientId", readLong(entityOrId, "clientId"));
        payload.put("balance", readRequiredLongValue(entityOrId, "balance"));
        copyIfPresent(source, payload, "id", "created", "createId", "updated", "updateId", "isDelete",
                "extend1", "extend2", "extend3");
        return payload;
    }

    /**
     * 解析 {@code channel} 域写入载荷。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return 通道 Hash 写入载荷
     */
    private Map<String, Object> resolveChannelPayload(Object entityOrId) {
        Map<String, Object> map = resolveHashPayload(entityOrId);
        Object channelNumber = firstNonNull(map, "channelNumber", "spNumber");
        if (channelNumber != null && !map.containsKey("channelNumber")) {
            map.put("channelNumber", channelNumber);
        }
        return map;
    }

    /**
     * 将对象成员快照解析为对象 Set 写入数组。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return 对象 Set 成员数组
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object>[] resolveSetMapMembers(Object entityOrId) {
        Object source = entityOrId;
        if (!(source instanceof Collection) && !(source instanceof Map[])) {
            Map<String, Object> map = toMap(entityOrId);
            source = firstNonNull(map, "members", "values");
        }

        List<Map<String, Object>> members = new ArrayList<>();
        if (source instanceof Map[]) {
            for (Map<?, ?> item : (Map<?, ?>[]) source) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                members.add(toMap(item));
            }
            return members.toArray(new Map[0]);
        }

        if (source instanceof Collection) {
            Collection<?> collection = (Collection<?>) source;
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                Map<String, Object> memberMap;
                if (item instanceof Map) {
                    memberMap = toMap(item);
                } else if (item instanceof String || item instanceof Number || item instanceof Boolean) {
                    continue;
                } else {
                    memberMap = toMap(item);
                }
                if (!memberMap.isEmpty()) {
                    members.add(memberMap);
                }
            }
        }
        return members.toArray(new Map[0]);
    }

    /**
     * 强校验集合型缓存快照载荷。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return 原始成员集合对象
     */
    private Object requireSnapshotPayload(Object entityOrId) {
        Map<String, Object> map = toMap(entityOrId);
        if (map.isEmpty()) {
            throw new ApiException("snapshot payload must contain clientId and members", ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        readLong(entityOrId, "clientId");
        if (map.containsKey("members")) {
            return map.get("members");
        }
        if (map.containsKey("values")) {
            return map.get("values");
        }
        throw new ApiException("snapshot payload must contain members/values", ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 将字符串成员快照解析为字符串 Set 写入数组。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @return 字符串 Set 成员数组
     */
    private String[] resolveSetMembers(Object entityOrId) {
        if (entityOrId == null) {
            return new String[0];
        }
        if (entityOrId instanceof String[]) {
            return normalizeMembers((String[]) entityOrId);
        }
        if (entityOrId instanceof Collection) {
            Collection<?> values = (Collection<?>) entityOrId;
            List<String> members = new ArrayList<>();
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                String normalized = value.toString().trim();
                if (!normalized.isEmpty()) {
                    members.add(normalized);
                }
            }
            return members.toArray(new String[0]);
        }

        Map<String, Object> map = toMap(entityOrId);
        Object membersValue = firstNonNull(map, "members", "values");
        if (membersValue instanceof Collection) {
            return resolveSetMembers(membersValue);
        }
        if (membersValue instanceof String[]) {
            return normalizeMembers((String[]) membersValue);
        }
        return new String[0];
    }

    /**
     * 解析字符串型缓存写入值。
     *
     * @param domain 缓存域编码
     * @param entityOrId 业务实体对象或主键标识
     * @return 字符串值
     */
    private String resolveStringValue(String domain, Object entityOrId) {
        Map<String, Object> map = toMap(entityOrId);
        Object value = firstNonNull(map, "value", "carrier", "operator");
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return String.valueOf(value).trim();
        }
        if (CacheDomainRegistry.BLACK.equals(domain)) {
            return "1";
        }
        throw new ApiException("value must not be blank for domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    /**
     * 读取并强校验文本字段。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @param aliases 可接受的字段别名列表
     * @return 文本字段值
     */
    private String readText(Object entityOrId, String... aliases) {
        if (entityOrId instanceof String) {
            String value = ((String) entityOrId).trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        Map<String, Object> map = toMap(entityOrId);
        Object value = firstNonNull(map, aliases);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new ApiException("required text field missing: " + String.join("/", aliases), ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return String.valueOf(value).trim();
    }

    /**
     * 读取并强校验正整数标识字段。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @param aliases 可接受的字段别名列表
     * @return 正整数标识值
     */
    private Long readLong(Object entityOrId, String... aliases) {
        Long value = tryReadLong(entityOrId, aliases);
        if (value == null || value <= 0) {
            throw new ApiException("required positive id missing: " + String.join("/", aliases), ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return value;
    }

    /**
     * 尝试读取正整数标识字段。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @param aliases 可接受的字段别名列表
     * @return 解析成功时返回正整数，否则返回 {@code null}
     */
    private Long tryReadLong(Object entityOrId, String... aliases) {
        if (entityOrId instanceof Number) {
            long value = ((Number) entityOrId).longValue();
            return value > 0 ? value : null;
        }
        if (entityOrId instanceof String && StringUtils.hasText((String) entityOrId)) {
            try {
                long value = Long.parseLong(((String) entityOrId).trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        Map<String, Object> map = toMap(entityOrId);
        Object value = firstNonNull(map, aliases);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long parsed = ((Number) value).longValue();
            return parsed > 0 ? parsed : null;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(text);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * 读取并强校验长整数字段。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @param aliases 可接受的字段别名列表
     * @return 长整数字段值
     */
    private Long readRequiredLongValue(Object entityOrId, String... aliases) {
        Long value = tryReadLongValue(entityOrId, aliases);
        if (value == null) {
            throw new ApiException("required long field missing: " + String.join("/", aliases), ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return value;
    }

    /**
     * 尝试读取长整数字段。
     *
     * @param entityOrId 业务实体对象或主键标识
     * @param aliases 可接受的字段别名列表
     * @return 解析成功时返回长整数，否则返回 {@code null}
     */
    private Long tryReadLongValue(Object entityOrId, String... aliases) {
        if (entityOrId instanceof Number) {
            return ((Number) entityOrId).longValue();
        }
        if (entityOrId instanceof String && StringUtils.hasText((String) entityOrId)) {
            try {
                return Long.parseLong(((String) entityOrId).trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        Map<String, Object> map = toMap(entityOrId);
        Object value = firstNonNull(map, aliases);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * 从 Map 中按别名顺序获取第一个非空字段值。
     *
     * @param map 来源 Map
     * @param aliases 字段别名列表
     * @return 第一个非空字段值；未命中时返回 {@code null}
     */
    private Object firstNonNull(Map<String, Object> map, String... aliases) {
        if (map == null || map.isEmpty() || aliases == null) {
            return null;
        }
        for (String alias : aliases) {
            if (!StringUtils.hasText(alias)) {
                continue;
            }
            if (map.containsKey(alias)) {
                Object value = map.get(alias);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 将来源 Map 中存在的字段复制到目标 Map。
     *
     * @param source 来源 Map
     * @param target 目标 Map
     * @param fields 需要复制的字段列表
     */
    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... fields) {
        if (source == null || source.isEmpty() || target == null || fields == null) {
            return;
        }
        for (String field : fields) {
            if (!StringUtils.hasText(field) || !source.containsKey(field)) {
                continue;
            }
            target.put(field, source.get(field));
        }
    }

    /**
     * 将对象转换为标准化的 Map 结构。
     *
     * @param value 原始对象
     * @return 标准化后的 Map；不可转换时返回空 Map
     */
    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) value;
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Collection || value instanceof String[]) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    /**
     * 规范化字符串成员数组。
     *
     * @param source 原始字符串成员数组
     * @return 去空白后的字符串成员数组
     */
    private String[] normalizeMembers(String[] source) {
        if (source == null || source.length == 0) {
            return new String[0];
        }
        List<String> members = new ArrayList<>(source.length);
        for (String value : source) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            members.add(value.trim());
        }
        return members.toArray(new String[0]);
    }

    /**
     * 计算从指定开始时间到当前时间的耗时。
     *
     * @param startAt 开始时间戳
     * @return 非负耗时毫秒值
     */
    private long costMs(long startAt) {
        return Math.max(System.currentTimeMillis() - startAt, 0);
    }

    /**
     * 解析当前已允许且已注册加载器的手工重建域列表。
     *
     * @return 当前可参与手工重建的域列表
     */
    private List<String> resolveCurrentRegisteredManualRebuildDomains() {
        List<String> domains = new ArrayList<>();
        for (String domainCode : CacheDomainRegistry.currentManualRebuildDomainCodes()) {
            if (domainRebuildLoaderRegistry.contains(domainCode)) {
                domains.add(domainCode);
            }
        }
        return domains;
    }

    /**
     * 构建“手工重建关闭”场景的跳过报告。
     *
     * @param domain 缓存域编码
     * @param startAt 开始时间戳
     * @param message 补充说明
     * @return 跳过报告
     */
    private CacheRebuildReport buildSkippedRebuildReport(String domain, long startAt, String message) {
        CacheRebuildReport report = new CacheRebuildReport();
        report.setTraceId(resolveTraceId());
        report.setTrigger(REBUILD_TRIGGER_MANUAL);
        report.setDomain(domain);
        report.setStartAt(startAt);
        report.setEndAt(System.currentTimeMillis());
        report.setAttemptedKeys(0);
        report.setSuccessCount(0);
        report.setFailCount(0);
        report.setDirtyReplay(false);
        report.setStatus("SKIPPED");
        report.setMessage(message);
        return report;
    }

    private CacheRebuildReport buildBootSkippedRebuildReport(String domain, long startAt, String message) {
        CacheRebuildReport report = buildSkippedRebuildReport(domain, startAt, message);
        report.setTrigger(REBUILD_TRIGGER_BOOT);
        return report;
    }

    private CacheRebuildReport adaptBootReport(CacheRebuildReport report, String operation, long startAt) {
        if (report == null) {
            CacheSyncLogHelper.info(log, UNKNOWN, "-", "-", operation, costMs(startAt));
            return null;
        }
        report.setTrigger(REBUILD_TRIGGER_BOOT);
        report.setMessage(rewriteManualMessageForBoot(report.getMessage()));
        CacheSyncLogHelper.info(log, report.getDomain(), "-", "-", operation, costMs(startAt));
        return report;
    }

    private ApiException adaptBootException(ApiException ex) {
        if (ex == null) {
            return null;
        }
        return new ApiException(rewriteManualMessageForBoot(ex.getMessage()), ex.getCode());
    }

    /**
     * 聚合多个单域报告为 `ALL` 总报告。
     *
     * @param domain 顶层域编码
     * @param startAt 开始时间戳
     * @param childReports 子报告列表
     * @return 聚合报告
     */
    private CacheRebuildReport buildAggregateRebuildReport(String domain,
                                                           long startAt,
                                                           List<CacheRebuildReport> childReports) {
        CacheRebuildReport report = new CacheRebuildReport();
        report.setTraceId(resolveTraceId());
        report.setTrigger("MANUAL");
        report.setDomain(domain);
        report.setStartAt(startAt);
        report.setEndAt(System.currentTimeMillis());
        report.setReports(childReports);

        int attemptedKeys = 0;
        int successCount = 0;
        int failCount = 0;
        boolean dirtyReplay = false;
        List<String> failedKeys = new ArrayList<>();
        if (childReports != null) {
            for (CacheRebuildReport childReport : childReports) {
                if (childReport == null) {
                    continue;
                }
                attemptedKeys += childReport.getAttemptedKeys();
                successCount += childReport.getSuccessCount();
                failCount += childReport.getFailCount();
                dirtyReplay = dirtyReplay || childReport.isDirtyReplay();
                if (childReport.getFailedKeys() != null && !childReport.getFailedKeys().isEmpty()) {
                    failedKeys.addAll(childReport.getFailedKeys());
                }
            }
        }
        report.setAttemptedKeys(attemptedKeys);
        report.setSuccessCount(successCount);
        report.setFailCount(failCount);
        report.setFailedKeys(failedKeys);
        report.setDirtyReplay(dirtyReplay);
        if (failCount == 0) {
            report.setStatus("SUCCESS");
            report.setMessage(dirtyReplay ? "manual rebuild succeeded with dirty replay" : "manual rebuild succeeded");
        } else if (successCount == 0) {
            report.setStatus("FAIL");
            report.setMessage("manual rebuild failed");
        } else {
            report.setStatus("PARTIAL");
            report.setMessage("manual rebuild partially succeeded");
        }
        return report;
    }

    /**
     * 初始化单域重建报告。
     *
     * @param domain 缓存域编码
     * @param startAt 开始时间戳
     * @return 初始化后的重建报告
     */
    private CacheRebuildReport initDomainRebuildReport(String domain, long startAt) {
        CacheRebuildReport report = new CacheRebuildReport();
        report.setTraceId(resolveTraceId());
        report.setTrigger("MANUAL");
        report.setDomain(domain);
        report.setStartAt(startAt);
        report.setEndAt(startAt);
        report.setAttemptedKeys(0);
        report.setSuccessCount(0);
        report.setFailCount(0);
        report.setDirtyReplay(false);
        report.setStatus("RUNNING");
        report.setMessage("manual rebuild running");
        return report;
    }

    /**
     * 执行一次重建阶段。
     *
     * <p>每次阶段都会先清理旧 key，再遍历加载器快照逐项回灌，
     * 并把成功、失败和失败 key 统计写入报告。</p>
     *
     * @param contract 缓存域契约
     * @param loader 域级重建加载器
     * @param report 当前重建报告
     * @param phase 阶段名称，例如 {@code initial}、{@code dirtyReplay}
     */
    private void executeRebuildPass(CacheDomainContract contract,
                                    DomainRebuildLoader loader,
                                    CacheRebuildReport report,
                                    String phase) {
        cleanupExistingDomainKeys(contract);
        List<Object> snapshot = normalizeSnapshot(loader.loadSnapshot());
        for (Object payload : snapshot) {
            String key = "-";
            long itemStartAt = System.currentTimeMillis();
            report.setAttemptedKeys(report.getAttemptedKeys() + 1);
            try {
                key = buildKey(contract.getDomainCode(), payload);
                doUpsert(contract.getDomainCode(), key, payload);
                report.setSuccessCount(report.getSuccessCount() + 1);
                CacheSyncLogHelper.info(log, contract.getDomainCode(), resolveEntityId(payload), key,
                        "rebuildDomain." + phase + ".item", costMs(itemStartAt));
            } catch (Exception ex) {
                report.setFailCount(report.getFailCount() + 1);
                report.getFailedKeys().add(normalizeFailedKey(key, payload));
                CacheSyncLogHelper.error(
                        log,
                        contract.getDomainCode(),
                        resolveEntityId(payload),
                        key,
                        "rebuildDomain." + phase + ".item",
                        costMs(itemStartAt),
                        ExceptionEnums.CACHE_SYNC_WRITE_FAIL,
                        ex.getMessage(),
                        ex
                );
            }
        }
    }

    /**
     * 清理当前缓存域在 Redis 中的旧 key。
     *
     * @param contract 缓存域契约
     */
    private void cleanupExistingDomainKeys(CacheDomainContract contract) {
        List<String> oldKeys = scanExistingDomainKeys(contract);
        if (oldKeys.isEmpty()) {
            return;
        }
        CacheDeleteResultDTO deleteResult = cacheWriteClient.deleteBatch(oldKeys);
        if (deleteResult != null && deleteResult.getFailedKeys() != null && !deleteResult.getFailedKeys().isEmpty()) {
            throw new ApiException("cleanup old keys failed for domain: " + contract.getDomainCode(),
                    ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
    }

    /**
     * 根据缓存域契约扫描当前已存在的旧 key。
     *
     * @param contract 缓存域契约
     * @return 当前命中的逻辑 key 列表
     */
    private List<String> scanExistingDomainKeys(CacheDomainContract contract) {
        if (contract == null || contract.getLogicalKeyPatterns() == null || contract.getLogicalKeyPatterns().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        for (String logicalKeyPattern : contract.getLogicalKeyPatterns()) {
            String scanPattern = toScanPattern(logicalKeyPattern);
            java.util.Set<String> matchedKeys = cacheWriteClient.keys(scanPattern, DEFAULT_SCAN_COUNT);
            if (matchedKeys == null || matchedKeys.isEmpty()) {
                continue;
            }
            for (String matchedKey : matchedKeys) {
                if (StringUtils.hasText(matchedKey) && !keys.contains(matchedKey.trim())) {
                    keys.add(matchedKey.trim());
                }
            }
        }
        return keys;
    }

    /**
     * 将逻辑 key 模板转换为 Redis 扫描 pattern。
     *
     * @param logicalKeyPattern 逻辑 key 模板
     * @return 可用于扫描的 pattern
     */
    private String toScanPattern(String logicalKeyPattern) {
        if (!StringUtils.hasText(logicalKeyPattern)) {
            throw new IllegalArgumentException("logicalKeyPattern must not be blank");
        }
        String pattern = logicalKeyPattern.trim().replaceAll("\\{[^}]+\\}", "*");
        return pattern.contains("*") ? pattern : pattern + "*";
    }

    /**
     * 规范化加载器返回的快照列表。
     *
     * @param snapshot 原始快照列表
     * @return 规范化后的快照列表
     */
    private List<Object> normalizeSnapshot(List<Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Collections.emptyList();
        }
        return snapshot;
    }

    /**
     * 根据统计结果更新单域重建报告最终状态。
     *
     * @param report 重建报告
     */
    private void applyFinalReportState(CacheRebuildReport report) {
        if (report.getFailCount() == 0) {
            report.setStatus("SUCCESS");
            report.setMessage(report.isDirtyReplay()
                    ? "manual rebuild succeeded with dirty replay"
                    : "manual rebuild succeeded");
            return;
        }
        if (report.getSuccessCount() == 0) {
            report.setStatus("FAIL");
            report.setMessage(report.isDirtyReplay()
                    ? "manual rebuild failed after dirty replay"
                    : "manual rebuild failed");
            return;
        }
        report.setStatus("PARTIAL");
        report.setMessage(report.isDirtyReplay()
                ? "manual rebuild partially succeeded with dirty replay"
                : "manual rebuild partially succeeded");
    }

    /**
     * 在重建流程发生异常时补充失败状态。
     *
     * @param report 当前重建报告
     * @param ex 异常对象
     */
    private void markEngineFailure(CacheRebuildReport report, Exception ex) {
        if (report == null) {
            return;
        }
        if (report.getFailCount() == 0) {
            report.setFailCount(1);
        }
        if (report.getFailedKeys().isEmpty()) {
            report.getFailedKeys().add(ex == null ? UNKNOWN : ex.getMessage());
        }
        report.setStatus(report.getSuccessCount() > 0 ? "PARTIAL" : "FAIL");
        report.setMessage(ex == null ? "manual rebuild failed" : ex.getMessage());
    }

    private String rewriteManualMessageForBoot(String message) {
        if (!StringUtils.hasText(message)) {
            return message;
        }
        return message.replace("manual rebuild", "boot reconcile");
    }

    /**
     * 规范化失败 key 展示值。
     *
     * @param key 已解析出的逻辑 key
     * @param payload 当前处理的快照载荷
     * @return 可追踪的失败 key 或实体标识
     */
    private String normalizeFailedKey(String key, Object payload) {
        if (StringUtils.hasText(key) && !UNKNOWN.equals(key)) {
            return key.trim();
        }
        return resolveEntityId(payload);
    }

    /**
     * 从 MDC 中解析当前 traceId。
     *
     * @return traceId；未命中时返回默认占位值
     */
    private String resolveTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (!StringUtils.hasText(traceId)) {
            traceId = MDC.get(TRACE_ID_KEY_UPPER);
        }
        return StringUtils.hasText(traceId) ? traceId.trim() : UNKNOWN;
    }
}
