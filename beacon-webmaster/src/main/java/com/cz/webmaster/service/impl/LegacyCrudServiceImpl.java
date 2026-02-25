package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.service.LegacyCrudService;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LegacyCrudServiceImpl implements LegacyCrudService {

    private static final String ACTIVITY = "activity";
    private static final String API_MAPPING = "apimapping";
    private static final String GRAY_RELEASE = "grayrelease";
    private static final String PUBLIC_PARAMS = "publicparams";
    private static final String BLACK = "black";
    private static final String NOTIFY = "notify";
    private static final String SEARCH_PARAMS = "searchparams";
    private static final String MESSAGE = "message";
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
                    API_GATEWAY_FILTER,
                    STRAGETY_FILTER,
                    LIMIT,
                    SMS_TEMP
            ))
    );

    private static final Map<String, String> DETAIL_KEY_MAP;
    private static final Map<String, List<String>> REQUIRED_FIELDS;

    static {
        Map<String, String> detailKeys = new LinkedHashMap<>();
        detailKeys.put(ACTIVITY, "activity");
        detailKeys.put(API_MAPPING, "apimapping");
        detailKeys.put(GRAY_RELEASE, "grayrelease");
        detailKeys.put(PUBLIC_PARAMS, "param");
        detailKeys.put(BLACK, "black");
        detailKeys.put(NOTIFY, "notify");
        detailKeys.put(SEARCH_PARAMS, "searchparams");
        detailKeys.put(MESSAGE, "message");
        detailKeys.put(API_GATEWAY_FILTER, "filter");
        detailKeys.put(STRAGETY_FILTER, "filter");
        detailKeys.put(LIMIT, "limit");
        detailKeys.put(SMS_TEMP, "smstemplate");
        DETAIL_KEY_MAP = Collections.unmodifiableMap(detailKeys);

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

    @Override
    public boolean supportsFamily(String family) {
        return SUPPORTED_FAMILIES.contains(family);
    }

    @Override
    public String getDetailKey(String family) {
        return DETAIL_KEY_MAP.get(family);
    }

    @Override
    public String validateForSave(String family, Map<String, Object> body) {
        if (!supportsFamily(family)) {
            return "unsupported family";
        }
        if (body == null) {
            return "request body is required";
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
        Map<String, Object> merged = new LinkedHashMap<>(current);
        merged.putAll(body);
        merged.put("id", id);
        fillFamilyDefaults(family, merged, System.currentTimeMillis(), operatorId, false);
        familyData.put(id, merged);
        return true;
    }

    @Override
    public boolean deleteBatch(String family, List<Long> ids) {
        if (!supportsFamily(family) || ids == null || ids.isEmpty()) {
            return false;
        }
        ConcurrentMap<Long, Map<String, Object>> familyData = familyStore(family);
        boolean removedAny = false;
        for (Long id : ids) {
            if (id != null && familyData.remove(id) != null) {
                removedAny = true;
            }
        }
        return removedAny;
    }

    private ConcurrentMap<Long, Map<String, Object>> familyStore(String family) {
        return store.computeIfAbsent(family, k -> new ConcurrentHashMap<>());
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

