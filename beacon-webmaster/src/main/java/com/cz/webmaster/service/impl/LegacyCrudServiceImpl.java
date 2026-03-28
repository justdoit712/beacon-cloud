package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.LegacyCrudService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LegacyCrudServiceImpl implements LegacyCrudService {

    private static final Logger log = LoggerFactory.getLogger(LegacyCrudServiceImpl.class);

    private static final String ACTIVITY = "activity";
    private static final String API_MAPPING = "apimapping";
    private static final String GRAY_RELEASE = "grayrelease";
    private static final String PUBLIC_PARAMS = "publicparams";
    private static final String BLACK = "black";
    private static final String NOTIFY = "notify";
    private static final String SEARCH_PARAMS = "searchparams";
    private static final String MESSAGE = "message";
    private static final String CLIENT_SIGN = "clientsign";
    private static final String CLIENT_TEMPLATE = "clienttemplate";
    private static final String API_GATEWAY_FILTER = "apigatewayfilter";
    private static final String STRAGETY_FILTER = "stragetyfilter";
    private static final String LIMIT = "limit";
    private static final String SMS_TEMP = "smstemp";

    private static final Set<String> SUPPORTED_FAMILIES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    ACTIVITY,
                    API_MAPPING,
                    GRAY_RELEASE,
                    PUBLIC_PARAMS,
                    BLACK,
                    NOTIFY,
                    SEARCH_PARAMS,
                    MESSAGE,
                    CLIENT_SIGN,
                    CLIENT_TEMPLATE,
                    API_GATEWAY_FILTER,
                    STRAGETY_FILTER,
                    LIMIT,
                    SMS_TEMP
            ))
    );

    private static final Map<String, List<String>> REQUIRED_FIELDS;

    static {
        Map<String, List<String>> requiredFieldMap = new LinkedHashMap<>();
        requiredFieldMap.put(ACTIVITY, Collections.singletonList("title"));
        requiredFieldMap.put(API_MAPPING, Arrays.asList("gatewayApiName", "serviceId", "insideApiUrl"));
        requiredFieldMap.put(GRAY_RELEASE, Arrays.asList("serviceId", "path"));
        requiredFieldMap.put(PUBLIC_PARAMS, Arrays.asList("paramName", "paramType"));
        requiredFieldMap.put(BLACK, Collections.singletonList("mobile"));
        requiredFieldMap.put(NOTIFY, Collections.singletonList("tag"));
        requiredFieldMap.put(SEARCH_PARAMS, Arrays.asList("name", "cloum"));
        requiredFieldMap.put(MESSAGE, Collections.singletonList("dirtyword"));
        requiredFieldMap.put(API_GATEWAY_FILTER, Collections.singletonList("filters"));
        requiredFieldMap.put(STRAGETY_FILTER, Collections.singletonList("filters"));
        requiredFieldMap.put(LIMIT, Arrays.asList("limitTime", "limitCount"));
        requiredFieldMap.put(SMS_TEMP, Collections.singletonList("template"));
        REQUIRED_FIELDS = Collections.unmodifiableMap(requiredFieldMap);
    }

    private final ConcurrentMap<String, ConcurrentMap<Long, Map<String, Object>>> store = new ConcurrentHashMap<>();
    @Autowired
    private CacheSyncService cacheSyncService;
    @Autowired
    private CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    @Override
    public boolean supportsFamily(String family) {
        return SUPPORTED_FAMILIES.contains(family);
    }

    @Override
    public String validateForSave(String family, Map<String, Object> body) {
        if (!supportsFamily(family)) {
            return "unsupported family";
        }
        if (body == null) {
            return "request body is required";
        }
        if (CLIENT_SIGN.equals(family)) {
            Long clientId = resolvePositiveLong(body, "clientId", "client_id");
            if (clientId == null) {
                return "clientId is required";
            }
            if (!StringUtils.hasText(resolveText(body, "signInfo", "sign_info"))) {
                return "signInfo is required";
            }
            return null;
        }
        if (CLIENT_TEMPLATE.equals(family)) {
            Long signId = resolvePositiveLong(body, "signId", "sign_id");
            if (signId == null) {
                return "signId is required";
            }
            if (!StringUtils.hasText(resolveText(body, "templateText", "template_text"))) {
                return "templateText is required";
            }
            return null;
        }
        List<String> requiredFields = REQUIRED_FIELDS.get(family);
        if (requiredFields == null || requiredFields.isEmpty()) {
            return null;
        }
        for (String field : requiredFields) {
            if (!StringUtils.hasText(toStr(body.get(field)))) {
                return field + " is required";
            }
        }
        return null;
    }

    @Override
    public String validateForUpdate(String family, Map<String, Object> body) {
        String validateResult = validateForSave(family, body);
        if (validateResult != null) {
            return validateResult;
        }
        Long id = toLong(body.get("id"));
        if (id == null) {
            return "id is required";
        }
        return null;
    }

    @Override
    public PageResult list(String family, String keyword, int offset, int limit) {
        if (!supportsFamily(family)) {
            return new PageResult(0L, new ArrayList<>());
        }
        List<Map<String, Object>> all = new ArrayList<>(familyStore(family).values());
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : all) {
            if (matches(item, normalizedKeyword)) {
                filtered.add(copy(item));
            }
        }
        filtered.sort(Comparator.comparingLong(this::idOf).reversed());

        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.max(limit, 0);
        int fromIndex = Math.min(safeOffset, filtered.size());
        int toIndex = Math.min(fromIndex + safeLimit, filtered.size());
        List<Map<String, Object>> rows = safeLimit == 0 ? new ArrayList<>() : filtered.subList(fromIndex, toIndex);
        return new PageResult(filtered.size(), rows);
    }

    @Override
    public Map<String, Object> info(String family, Long id) {
        if (!supportsFamily(family) || id == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> data = familyStore(family).get(id);
        return data == null ? new LinkedHashMap<>() : copy(data);
    }

    @Override
    public boolean save(String family, Map<String, Object> body, Long operatorId) {
        if (!supportsFamily(family) || body == null) {
            return false;
        }
        Map<String, Object> row = new LinkedHashMap<>(body);
        Long id = toLong(row.get("id"));
        if (id == null) {
            id = IdUtil.getSnowflakeNextId();
            row.put("id", id);
        }
        long now = System.currentTimeMillis();
        fillFamilyDefaults(family, row, now, operatorId, true);
        familyStore(family).put(id, row);
        triggerRuntimeSyncOnSaveOrUpdate(family, null, row);
        return true;
    }

    @Override
    public boolean update(String family, Map<String, Object> body, Long operatorId) {
        if (!supportsFamily(family) || body == null) {
            return false;
        }
        Long id = toLong(body.get("id"));
        if (id == null) {
            return false;
        }
        ConcurrentMap<Long, Map<String, Object>> familyData = familyStore(family);
        Map<String, Object> current = familyData.get(id);
        if (current == null) {
            return false;
        }
        Map<String, Object> before = copy(current);
        Map<String, Object> merged = new LinkedHashMap<>(current);
        merged.putAll(body);
        merged.put("id", id);
        fillFamilyDefaults(family, merged, System.currentTimeMillis(), operatorId, false);
        familyData.put(id, merged);
        triggerRuntimeSyncOnSaveOrUpdate(family, before, merged);
        return true;
    }

    @Override
    public boolean deleteBatch(String family, List<Long> ids) {
        if (!supportsFamily(family) || ids == null || ids.isEmpty()) {
            return false;
        }
        ConcurrentMap<Long, Map<String, Object>> familyData = familyStore(family);
        boolean removedAny = false;
        List<Map<String, Object>> removedRows = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) {
                Map<String, Object> removed = familyData.remove(id);
                if (removed != null) {
                    removedRows.add(copy(removed));
                    removedAny = true;
                }
            }
        }
        if (removedAny) {
            triggerRuntimeSyncOnDelete(family, removedRows);
        }
        return removedAny;
    }

    private ConcurrentMap<Long, Map<String, Object>> familyStore(String family) {
        return store.computeIfAbsent(family, k -> new ConcurrentHashMap<>());
    }

    private void triggerRuntimeSyncOnSaveOrUpdate(String family,
                                                  Map<String, Object> before,
                                                  Map<String, Object> after) {
        if (BLACK.equals(family)) {
            syncBlackSaveOrUpdate(before, after);
            return;
        }
        if (MESSAGE.equals(family)) {
            syncDirtyWordRebuild(toLong(after == null ? null : after.get("id")));
            return;
        }
        if (CLIENT_SIGN.equals(family)) {
            syncClientSignSaveOrUpdate(before, after);
            return;
        }
        if (CLIENT_TEMPLATE.equals(family)) {
            syncClientTemplateSaveOrUpdate(before, after);
            return;
        }
        if (SEARCH_PARAMS.equals(family)) {
            syncTransferSaveOrUpdate(before, after);
            return;
        }
        log.debug("runtime sync skip unsupported legacy family on save/update: family={}", family);
    }

    private void triggerRuntimeSyncOnDelete(String family, List<Map<String, Object>> removedRows) {
        if (BLACK.equals(family)) {
            for (Map<String, Object> row : removedRows) {
                syncBlackDelete(row);
            }
            return;
        }
        if (MESSAGE.equals(family)) {
            syncDirtyWordRebuild(null);
            return;
        }
        if (CLIENT_SIGN.equals(family)) {
            for (Map<String, Object> row : removedRows) {
                syncClientSignDelete(row);
            }
            return;
        }
        if (CLIENT_TEMPLATE.equals(family)) {
            for (Map<String, Object> row : removedRows) {
                syncClientTemplateDelete(row);
            }
            return;
        }
        if (SEARCH_PARAMS.equals(family)) {
            for (Map<String, Object> row : removedRows) {
                syncTransferDelete(row);
            }
            return;
        }
        log.debug("runtime sync skip unsupported legacy family on delete: family={}", family);
    }

    private void syncBlackSaveOrUpdate(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> beforePayload = resolveBlackPayload(before);
        Map<String, Object> afterPayload = resolveBlackPayload(after);
        String beforeIdentity = blackIdentity(beforePayload);
        String afterIdentity = blackIdentity(afterPayload);

        Long entityId = toLong(after == null ? null : after.get("id"));
        if (StringUtils.hasText(beforeIdentity)
                && !beforeIdentity.equals(afterIdentity)
                && beforePayload != null) {
            scheduleSync(CacheDomainRegistry.BLACK, "delete", safeEntityId(entityId),
                    () -> cacheSyncService.syncDelete(CacheDomainRegistry.BLACK, beforePayload));
        }
        if (afterPayload != null) {
            scheduleSync(CacheDomainRegistry.BLACK, "upsert", safeEntityId(entityId),
                    () -> cacheSyncService.syncUpsert(CacheDomainRegistry.BLACK, afterPayload));
        } else {
            log.debug("runtime sync skip black because payload is invalid, row={}", after);
        }
    }

    private void syncBlackDelete(Map<String, Object> row) {
        Map<String, Object> payload = resolveBlackPayload(row);
        if (payload == null) {
            return;
        }
        Long entityId = toLong(row == null ? null : row.get("id"));
        scheduleSync(CacheDomainRegistry.BLACK, "delete", safeEntityId(entityId),
                () -> cacheSyncService.syncDelete(CacheDomainRegistry.BLACK, payload));
    }

    private void syncDirtyWordRebuild(Long entityId) {
        List<String> members = collectDirtyWords();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("members", members);
        scheduleSync(CacheDomainRegistry.DIRTY_WORD, "upsert", safeEntityId(entityId),
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.DIRTY_WORD, payload));
    }

    private void syncClientSignSaveOrUpdate(Map<String, Object> before, Map<String, Object> after) {
        Long beforeClientId = resolvePositiveLong(before, "clientId", "client_id");
        Long afterClientId = resolvePositiveLong(after, "clientId", "client_id");
        Long entityId = toLong(after == null ? null : after.get("id"));

        if (beforeClientId != null && !Objects.equals(beforeClientId, afterClientId)) {
            Map<String, Object> beforePayload = buildClientSignSnapshotPayload(beforeClientId);
            scheduleSync(CacheDomainRegistry.CLIENT_SIGN, "upsert", safeEntityId(entityId),
                    () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_SIGN, beforePayload));
        }
        if (afterClientId != null) {
            Map<String, Object> afterPayload = buildClientSignSnapshotPayload(afterClientId);
            scheduleSync(CacheDomainRegistry.CLIENT_SIGN, "upsert", safeEntityId(entityId),
                    () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_SIGN, afterPayload));
        } else {
            log.debug("runtime sync skip clientsign because payload is invalid, row={}", after);
        }
    }

    private void syncClientSignDelete(Map<String, Object> row) {
        Long clientId = resolvePositiveLong(row, "clientId", "client_id");
        if (clientId == null) {
            return;
        }
        Long entityId = toLong(row == null ? null : row.get("id"));
        Map<String, Object> payload = buildClientSignSnapshotPayload(clientId);
        scheduleSync(CacheDomainRegistry.CLIENT_SIGN, "upsert", safeEntityId(entityId),
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_SIGN, payload));
    }

    private void syncClientTemplateSaveOrUpdate(Map<String, Object> before, Map<String, Object> after) {
        Long beforeSignId = resolvePositiveLong(before, "signId", "sign_id");
        Long afterSignId = resolvePositiveLong(after, "signId", "sign_id");
        Long entityId = toLong(after == null ? null : after.get("id"));

        if (beforeSignId != null && !Objects.equals(beforeSignId, afterSignId)) {
            Map<String, Object> beforePayload = buildClientTemplateSnapshotPayload(beforeSignId);
            scheduleSync(CacheDomainRegistry.CLIENT_TEMPLATE, "upsert", safeEntityId(entityId),
                    () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_TEMPLATE, beforePayload));
        }
        if (afterSignId != null) {
            Map<String, Object> afterPayload = buildClientTemplateSnapshotPayload(afterSignId);
            scheduleSync(CacheDomainRegistry.CLIENT_TEMPLATE, "upsert", safeEntityId(entityId),
                    () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_TEMPLATE, afterPayload));
        } else {
            log.debug("runtime sync skip clienttemplate because payload is invalid, row={}", after);
        }
    }

    private void syncClientTemplateDelete(Map<String, Object> row) {
        Long signId = resolvePositiveLong(row, "signId", "sign_id");
        if (signId == null) {
            return;
        }
        Long entityId = toLong(row == null ? null : row.get("id"));
        Map<String, Object> payload = buildClientTemplateSnapshotPayload(signId);
        scheduleSync(CacheDomainRegistry.CLIENT_TEMPLATE, "upsert", safeEntityId(entityId),
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_TEMPLATE, payload));
    }

    private void syncTransferSaveOrUpdate(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> beforePayload = resolveTransferPayload(before);
        Map<String, Object> afterPayload = resolveTransferPayload(after);
        String beforeIdentity = transferIdentity(beforePayload);
        String afterIdentity = transferIdentity(afterPayload);
        Long entityId = toLong(after == null ? null : after.get("id"));

        if (StringUtils.hasText(beforeIdentity)
                && !beforeIdentity.equals(afterIdentity)
                && beforePayload != null) {
            scheduleSync(CacheDomainRegistry.TRANSFER, "delete", safeEntityId(entityId),
                    () -> cacheSyncService.syncDelete(CacheDomainRegistry.TRANSFER, beforePayload));
        }
        if (afterPayload != null) {
            scheduleSync(CacheDomainRegistry.TRANSFER, "upsert", safeEntityId(entityId),
                    () -> cacheSyncService.syncUpsert(CacheDomainRegistry.TRANSFER, afterPayload));
        } else {
            log.debug("runtime sync skip searchparams->transfer because payload is invalid, row={}", after);
        }
    }

    private void syncTransferDelete(Map<String, Object> row) {
        Map<String, Object> payload = resolveTransferPayload(row);
        if (payload == null) {
            return;
        }
        Long entityId = toLong(row == null ? null : row.get("id"));
        scheduleSync(CacheDomainRegistry.TRANSFER, "delete", safeEntityId(entityId),
                () -> cacheSyncService.syncDelete(CacheDomainRegistry.TRANSFER, payload));
    }

    private List<String> collectDirtyWords() {
        Set<String> words = new LinkedHashSet<>();
        for (Map<String, Object> row : familyStore(MESSAGE).values()) {
            String word = resolveText(row, "dirtyword", "word", "keyword", "value");
            if (StringUtils.hasText(word)) {
                words.add(word.trim());
            }
        }
        return new ArrayList<>(words);
    }

    private Map<String, Object> buildClientSignSnapshotPayload(Long clientId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", clientId);
        payload.put("members", collectClientSignMembers(clientId));
        return payload;
    }

    private List<Map<String, Object>> collectClientSignMembers(Long clientId) {
        if (clientId == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> members = new ArrayList<>();
        for (Map<String, Object> row : familyStore(CLIENT_SIGN).values()) {
            Long rowClientId = resolvePositiveLong(row, "clientId", "client_id");
            if (!Objects.equals(clientId, rowClientId)) {
                continue;
            }
            Map<String, Object> member = copy(row);
            member.remove("clientId");
            member.remove("client_id");
            members.add(member);
        }
        members.sort(Comparator.comparingLong(this::idOf));
        return members;
    }

    private Map<String, Object> buildClientTemplateSnapshotPayload(Long signId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signId", signId);
        payload.put("members", collectClientTemplateMembers(signId));
        return payload;
    }

    private List<Map<String, Object>> collectClientTemplateMembers(Long signId) {
        if (signId == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> members = new ArrayList<>();
        for (Map<String, Object> row : familyStore(CLIENT_TEMPLATE).values()) {
            Long rowSignId = resolvePositiveLong(row, "signId", "sign_id");
            if (!Objects.equals(signId, rowSignId)) {
                continue;
            }
            Map<String, Object> member = copy(row);
            member.remove("signId");
            member.remove("sign_id");
            members.add(member);
        }
        members.sort(Comparator.comparingLong(this::idOf));
        return members;
    }

    private Map<String, Object> resolveBlackPayload(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String mobile = resolveText(row, "mobile", "blackNumber", "black_number", "number");
        if (!StringUtils.hasText(mobile)) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobile", mobile.trim());
        Long clientId = resolvePositiveLong(row, "clientId", "client_id", "owntypeid", "ownerId");
        if (clientId != null) {
            payload.put("clientId", clientId);
        }
        return payload;
    }

    private Map<String, Object> resolveTransferPayload(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String mobile = resolveText(row, "mobile", "transferNumber", "transfer_number", "number");
        if (!StringUtils.hasText(mobile)) {
            return null;
        }
        String value = resolveText(row, "value", "operator", "carrier", "nowIsp", "now_isp", "mobileType", "mobile_type");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobile", mobile.trim());
        payload.put("value", value.trim());
        return payload;
    }

    private Long resolvePositiveLong(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Long value = toLong(row.get(key));
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private String resolveText(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString();
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private String blackIdentity(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String mobile = resolveText(payload, "mobile");
        if (!StringUtils.hasText(mobile)) {
            return null;
        }
        Long clientId = resolvePositiveLong(payload, "clientId");
        return clientId == null ? mobile : clientId + ":" + mobile;
    }

    private String transferIdentity(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        return resolveText(payload, "mobile");
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }

    private void scheduleSync(String domain, String operation, String entityId, Runnable action) {
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(action, domain, operation, entityId);
    }

    private void fillFamilyDefaults(String family,
                                    Map<String, Object> row,
                                    long now,
                                    Long operatorId,
                                    boolean isCreate) {
        if (isCreate && row.get("created") == null) {
            row.put("created", now);
        }
        row.put("updated", now);

        if (operatorId != null) {
            if (isCreate && row.get("createId") == null) {
                row.put("createId", operatorId);
            }
            row.put("updateId", operatorId);
            if (row.get("creater") == null) {
                row.put("creater", operatorId.toString());
            }
        }

        if (isCreate && row.get("owntype") == null && (BLACK.equals(family) || MESSAGE.equals(family) || SMS_TEMP.equals(family))) {
            row.put("owntype", 1);
        }

        if (API_MAPPING.equals(family)) {
            if (isCreate && row.get("createDate") == null) {
                row.put("createDate", now);
            }
            row.put("updateTime", now);
            if (row.get("state") == null) {
                row.put("state", 1);
            }
        } else if (PUBLIC_PARAMS.equals(family)) {
            if (isCreate && row.get("createDate") == null) {
                row.put("createDate", now);
            }
            if (row.get("isMust") == null) {
                row.put("isMust", 0);
            }
            if (row.get("enableState") == null) {
                row.put("enableState", 1);
            }
        } else if (GRAY_RELEASE.equals(family)) {
            if (row.get("state") == null) {
                row.put("state", 1);
            }
        } else if (NOTIFY.equals(family)) {
            if (row.get("notifyState") == null) {
                row.put("notifyState", 1);
            }
            if (row.get("cacheState") == null) {
                row.put("cacheState", 1);
            }
        } else if (SEARCH_PARAMS.equals(family)) {
            if (row.get("state") == null) {
                row.put("state", 1);
            }
        } else if (API_GATEWAY_FILTER.equals(family) || STRAGETY_FILTER.equals(family)) {
            if (row.get("filterState") == null) {
                row.put("filterState", 1);
            }
        } else if (LIMIT.equals(family)) {
            if (row.get("limitState") == null) {
                row.put("limitState", 1);
            }
        } else if (SMS_TEMP.equals(family)) {
            if (row.get("status") == null) {
                row.put("status", 1);
            }
        }
    }

    private boolean matches(Map<String, Object> row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        for (Object value : row.values()) {
            if (value != null && value.toString().toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private long idOf(Map<String, Object> row) {
        Long id = toLong(row.get("id"));
        return id == null ? 0L : id;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }
}
