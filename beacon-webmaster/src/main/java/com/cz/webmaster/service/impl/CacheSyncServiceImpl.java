package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDeletePolicy;
import com.cz.common.constant.CacheDomainContract;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import com.cz.webmaster.client.BeaconCacheWriteClient;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.rebuild.CacheRebuildCoordinationSupport;
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
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_KEY_UPPER = "TraceId";
    private static final String UNKNOWN = "-";

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

            CacheDomainContract contract = requireDomainContract(normalizedDomain);
            CacheRebuildReport report = rebuildSingleDomain(contract.getDomainCode());
            CacheSyncLogHelper.info(log, contract.getDomainCode(), "-", "-", operation, costMs(startAt));
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
     * 执行单个缓存域的手工重建骨架流程。
     *
     * <p>当前方法负责单域重建阶段的并发协调与报告骨架生成，主要包括：</p>
     * <p>1. 尝试获取域级重建锁，避免同域重建并发进入；</p>
     * <p>2. 生成当前阶段的结构化重建报告；</p>
     * <p>3. 在结束前消费脏标记，并将补跑观测结果写入报告；</p>
     * <p>4. 最终释放本次重建持有的域级锁。</p>
     *
     * <p>实际的“清旧 key、加载快照、回灌 Redis”重建引擎逻辑仍在后续阶段补充。</p>
     *
     * @param domain 缓存域编码
     * @return 单域重建报告
     */
    private CacheRebuildReport rebuildSingleDomain(String domain) {
        long startAt = System.currentTimeMillis();
        String lockToken = UUID.randomUUID().toString();
        // 单域手工重建先尝试获取域级锁，避免同域重复进入重建流程。
        if (cacheRebuildCoordinationSupport != null
                && !cacheRebuildCoordinationSupport.tryAcquireRebuildLock(domain, lockToken)) {
            throw new ApiException("manual rebuild domain busy: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }

        try {
            CacheSyncLogHelper.info(log, domain, "-", "-", "rebuildDomain.skeleton", 0L);
            // 当前阶段先返回结构化骨架报告，后续再在此处接入真正的重建引擎统计结果。
            CacheRebuildReport report = new CacheRebuildReport();
            report.setTraceId(resolveTraceId());
            report.setTrigger("MANUAL");
            report.setDomain(domain);
            report.setStartAt(startAt);
            report.setEndAt(System.currentTimeMillis());
            report.setAttemptedKeys(0);
            report.setSuccessCount(0);
            report.setFailCount(0);
            report.setDirtyReplay(false);
            report.setStatus("SKELETON");
            report.setMessage("manual rebuild report interface ready; rebuild engine not implemented yet");

            boolean dirtyReplay = false;
            // 若重建窗口期内有运行时同步被避让为脏标记，这里消费掉并记录为补跑观测信号。
            while (cacheRebuildCoordinationSupport != null && cacheRebuildCoordinationSupport.consumeDirty(domain)) {
                dirtyReplay = true;
                CacheSyncLogHelper.info(log, domain, "-", "-", "rebuildDomain.dirtyReplay.skeleton", 0L);
            }
            report.setDirtyReplay(dirtyReplay);
            if (dirtyReplay) {
                report.setMessage("manual rebuild report interface ready; dirty replay observed; rebuild engine not implemented yet");
            }
            report.setEndAt(System.currentTimeMillis());
            return report;
        } finally {
            // 无论骨架流程是否成功结束，都尝试释放本次持有的域级锁。
            if (cacheRebuildCoordinationSupport != null) {
                cacheRebuildCoordinationSupport.releaseRebuildLock(domain, lockToken);
            }
        }
    }

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

    private boolean isLegacySetDomain(String domain) {
        return CacheDomainRegistry.CLIENT_SIGN.equals(domain)
                || CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain)
                || CacheDomainRegistry.DIRTY_WORD.equals(domain);
    }

    private boolean isLegacyObjectSetDomain(String domain) {
        return CacheDomainRegistry.CLIENT_SIGN.equals(domain)
                || CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain);
    }

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

    private String buildKey(String domain, Object entityOrId) {
        if (CacheDomainRegistry.isCurrentMainlineDomain(domain)) {
            return buildCurrentMainlineKey(domain, entityOrId);
        }
        if (CacheDomainRegistry.isCurrentLegacyCompatibleDomain(domain)) {
            return buildLegacyCompatibleKey(domain, entityOrId);
        }
        throw new ApiException("unsupported cache domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

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

    private CacheDomainContract requireDomainContract(String domain) {
        try {
            return CacheDomainRegistry.require(domain);
        } catch (Exception ex) {
            throw new ApiException("unsupported cache domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
    }

    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            throw new ApiException("domain must not be blank", ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return domain.trim().toLowerCase(Locale.ROOT);
    }

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

    private Map<String, Object> resolveHashPayload(Object entityOrId) {
        Map<String, Object> map = toMap(entityOrId);
        return map.isEmpty() ? new LinkedHashMap<>() : map;
    }

    private Map<String, Object> resolveClientBusinessPayload(Object entityOrId) {
        Map<String, Object> payload = resolveHashPayload(entityOrId);
        readText(payload, "apiKey", "apikey");
        return payload;
    }

    private Map<String, Object> resolveClientBalancePayload(Object entityOrId) {
        Map<String, Object> source = resolveHashPayload(entityOrId);
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("clientId", readLong(entityOrId, "clientId"));
        payload.put("balance", readRequiredLongValue(entityOrId, "balance"));
        copyIfPresent(source, payload, "id", "created", "createId", "updated", "updateId", "isDelete",
                "extend1", "extend2", "extend3");
        return payload;
    }

    private Map<String, Object> resolveChannelPayload(Object entityOrId) {
        Map<String, Object> map = resolveHashPayload(entityOrId);
        Object channelNumber = firstNonNull(map, "channelNumber", "spNumber");
        if (channelNumber != null && !map.containsKey("channelNumber")) {
            map.put("channelNumber", channelNumber);
        }
        return map;
    }

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

    private Long readLong(Object entityOrId, String... aliases) {
        Long value = tryReadLong(entityOrId, aliases);
        if (value == null || value <= 0) {
            throw new ApiException("required positive id missing: " + String.join("/", aliases), ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return value;
    }

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

    private Long readRequiredLongValue(Object entityOrId, String... aliases) {
        Long value = tryReadLongValue(entityOrId, aliases);
        if (value == null) {
            throw new ApiException("required long field missing: " + String.join("/", aliases), ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        }
        return value;
    }

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

    private long costMs(long startAt) {
        return Math.max(System.currentTimeMillis() - startAt, 0);
    }

    private List<String> resolveCurrentRegisteredManualRebuildDomains() {
        List<String> domains = new ArrayList<>();
        for (String domainCode : CacheDomainRegistry.currentManualRebuildDomainCodes()) {
            if (domainRebuildLoaderRegistry.contains(domainCode)) {
                domains.add(domainCode);
            }
        }
        return domains;
    }

    private CacheRebuildReport buildSkippedRebuildReport(String domain, long startAt, String message) {
        CacheRebuildReport report = new CacheRebuildReport();
        report.setTraceId(resolveTraceId());
        report.setTrigger("MANUAL");
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
        report.setStatus("SKELETON");
        report.setMessage("manual rebuild report interface ready; rebuild engine not implemented yet");
        return report;
    }

    private String resolveTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (!StringUtils.hasText(traceId)) {
            traceId = MDC.get(TRACE_ID_KEY_UPPER);
        }
        return StringUtils.hasText(traceId) ? traceId.trim() : UNKNOWN;
    }
}
