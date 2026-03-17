package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainContract;
import com.cz.common.constant.CacheDeletePolicy;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import com.cz.webmaster.client.BeaconCacheWriteClient;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheKeyBuilder;
import com.cz.webmaster.support.CacheSyncLogHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CacheSyncService 骨架实现。
 * <p>
 * 第一层职责：
 * <p>
 * 1. 域路由：按 CacheDomainRegistry 解析域契约；<br>
 * 2. key 构建：统一调用 CacheKeyBuilder；<br>
 * 3. 写删调用：统一通过 BeaconCacheWriteClient；<br>
 * 4. 统一日志与错误码：复用 CacheSyncLogHelper + ExceptionEnums。<br>
 * <p>
 * 注意：本类当前是“骨架版本”，重建数据装配逻辑在后续层逐步补齐。
 */
@Service
public class CacheSyncServiceImpl implements CacheSyncService {

    private static final Logger log = LoggerFactory.getLogger(CacheSyncServiceImpl.class);
    private static final String DOMAIN_ALL = "ALL";

    private final CacheSyncProperties cacheSyncProperties;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final BeaconCacheWriteClient cacheWriteClient;
    private final ObjectMapper objectMapper;

    public CacheSyncServiceImpl(CacheSyncProperties cacheSyncProperties,
                                CacheKeyBuilder cacheKeyBuilder,
                                BeaconCacheWriteClient cacheWriteClient,
                                ObjectMapper objectMapper) {
        this.cacheSyncProperties = cacheSyncProperties;
        this.cacheKeyBuilder = cacheKeyBuilder;
        this.cacheWriteClient = cacheWriteClient;
        this.objectMapper = objectMapper;
    }

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

            // client_balance 删除策略为 OVERWRITE_ONLY：第一层先保守跳过删 key。
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

    @Override
    public void rebuildDomain(String domain) {
        long startAt = System.currentTimeMillis();
        String operation = "rebuildDomain";
        String normalizedDomain = normalizeDomain(domain);
        try {
            if (!cacheSyncProperties.isEnabled() || !cacheSyncProperties.getManual().isEnabled()) {
                CacheSyncLogHelper.info(log, normalizedDomain, "-", "-", operation + ".skip", costMs(startAt));
                return;
            }

            if (DOMAIN_ALL.equalsIgnoreCase(normalizedDomain)) {
                for (String allowedDomain : CacheDomainRegistry.currentManualRebuildDomainCodes()) {
                    rebuildSingleDomain(allowedDomain);
                }
                CacheSyncLogHelper.info(log, DOMAIN_ALL, "-", "-", operation, costMs(startAt));
                return;
            }

            if (!CacheDomainRegistry.isCurrentMainlineDomain(normalizedDomain)) {
                throw new ApiException("unsupported manual rebuild domain: " + normalizedDomain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }
            if (!CacheDomainRegistry.isCurrentManualRebuildDomain(normalizedDomain)) {
                throw new ApiException("manual rebuild domain not allowed yet: " + normalizedDomain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
            }

            CacheDomainContract contract = requireDomainContract(normalizedDomain);
            rebuildSingleDomain(contract.getDomainCode());
            CacheSyncLogHelper.info(log, contract.getDomainCode(), "-", "-", operation, costMs(startAt));
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

    private void rebuildSingleDomain(String domain) {
        // 第一层骨架：这里只做域路由占位。后续第三/四层在此补全“删除旧 key + DB 拉取 + 回灌”逻辑。
        CacheSyncLogHelper.info(log, domain, "-", "-", "rebuildDomain.skeleton", 0L);
    }

    private void doUpsert(String domain, String key, Object entityOrId) {
        if (CacheDomainRegistry.CLIENT_BUSINESS.equals(domain)
                || CacheDomainRegistry.CLIENT_BALANCE.equals(domain)) {
            cacheWriteClient.hmset(key, resolveHashPayload(entityOrId));
            return;
        }

        if (CacheDomainRegistry.CHANNEL.equals(domain)) {
            cacheWriteClient.hmset(key, resolveChannelPayload(entityOrId));
            return;
        }

        if (isSetDomain(domain)) {
            rebuildSetDomain(domain, key, entityOrId);
            return;
        }

        if (CacheDomainRegistry.BLACK.equals(domain) || CacheDomainRegistry.TRANSFER.equals(domain)) {
            cacheWriteClient.set(key, resolveStringValue(domain, entityOrId));
            return;
        }

        throw new ApiException("unsupported upsert domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
    }

    private boolean isSetDomain(String domain) {
        return CacheDomainRegistry.CLIENT_SIGN.equals(domain)
                || CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain)
                || CacheDomainRegistry.CLIENT_CHANNEL.equals(domain)
                || CacheDomainRegistry.DIRTY_WORD.equals(domain);
    }

    private boolean isObjectSetDomain(String domain) {
        return CacheDomainRegistry.CLIENT_SIGN.equals(domain)
                || CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain)
                || CacheDomainRegistry.CLIENT_CHANNEL.equals(domain);
    }

    private void rebuildSetDomain(String domain, String key, Object entityOrId) {
        cacheWriteClient.delete(key);
        if (isObjectSetDomain(domain)) {
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
        if (CacheDomainRegistry.CLIENT_BUSINESS.equals(domain)) {
            return cacheKeyBuilder.clientBusinessByApiKey(readText(entityOrId, "apiKey", "apikey"));
        }
        if (CacheDomainRegistry.CLIENT_BALANCE.equals(domain)) {
            return cacheKeyBuilder.clientBalanceByClientId(readLong(entityOrId, "clientId", "id"));
        }
        if (CacheDomainRegistry.CLIENT_SIGN.equals(domain)) {
            return cacheKeyBuilder.clientSignByClientId(readLong(entityOrId, "clientId", "id"));
        }
        if (CacheDomainRegistry.CLIENT_TEMPLATE.equals(domain)) {
            return cacheKeyBuilder.clientTemplateBySignId(readLong(entityOrId, "signId", "id"));
        }
        if (CacheDomainRegistry.CLIENT_CHANNEL.equals(domain)) {
            return cacheKeyBuilder.clientChannelByClientId(readLong(entityOrId, "clientId", "id"));
        }
        if (CacheDomainRegistry.CHANNEL.equals(domain)) {
            return cacheKeyBuilder.channelById(readLong(entityOrId, "id", "channelId"));
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
        throw new ApiException("unsupported cache domain: " + domain, ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
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
}
