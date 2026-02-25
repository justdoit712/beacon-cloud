package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.service.PhaseService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PhaseServiceImpl implements PhaseService {

    private static final List<Map<String, Object>> PROVINCES;
    private static final Map<Long, List<Map<String, Object>>> PROV_CITY_MAP;

    static {
        List<Map<String, Object>> provs = new LinkedList<>();
        provs.add(prov(11L, "鍖椾含"));
        provs.add(prov(31L, "涓婃捣"));
        provs.add(prov(44L, "骞夸笢"));
        PROVINCES = Collections.unmodifiableList(provs);

        Map<Long, List<Map<String, Object>>> cityMap = new LinkedHashMap<>();
        cityMap.put(11L, cityList(city(1101L, 11L, "鍖椾含甯?")));
        cityMap.put(31L, cityList(city(3101L, 31L, "涓婃捣甯?")));
        cityMap.put(44L, cityList(
                city(4401L, 44L, "骞垮窞甯?"),
                city(4403L, 44L, "娣卞湷甯?")
        ));
        PROV_CITY_MAP = Collections.unmodifiableMap(cityMap);
    }

    private final ConcurrentMap<Long, Map<String, Object>> dataStore = new ConcurrentHashMap<>();

    @Override
    public PageResult list(String keyword, int offset, int limit) {
        List<Map<String, Object>> all = new ArrayList<>(dataStore.values());
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : all) {
            if (matches(row, normalizedKeyword)) {
                filtered.add(copy(row));
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
    public Map<String, Object> info(Long id) {
        if (id == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> row = dataStore.get(id);
        return row == null ? new LinkedHashMap<>() : copy(row);
    }

    @Override
    public String validateForSave(Map<String, Object> body) {
        if (body == null) {
            return "request body is required";
        }
        if (!StringUtils.hasText(toStr(body.get("phase")))) {
            return "phase is required";
        }
        Long provId = toLong(body.get("provId"));
        Long cityId = toLong(body.get("cityId"));
        if (provId == null) {
            return "provId is required";
        }
        if (cityId == null) {
            return "cityId is required";
        }
        if (findProvinceName(provId) == null) {
            return "provId not found";
        }
        if (findCity(provId, cityId) == null) {
            return "cityId not found";
        }
        return null;
    }

    @Override
    public String validateForUpdate(Map<String, Object> body) {
        String validateResult = validateForSave(body);
        if (validateResult != null) {
            return validateResult;
        }
        if (toLong(body.get("id")) == null) {
            return "id is required";
        }
        return null;
    }

    @Override
    public boolean save(Map<String, Object> body, Long operatorId) {
        Long provId = toLong(body.get("provId"));
        Long cityId = toLong(body.get("cityId"));
        if (provId == null || cityId == null) {
            return false;
        }
        Map<String, Object> city = findCity(provId, cityId);
        String provName = findProvinceName(provId);
        if (city == null || provName == null) {
            return false;
        }

        Long id = toLong(body.get("id"));
        if (id == null) {
            id = IdUtil.getSnowflakeNextId();
        }
        long now = System.currentTimeMillis();

        Map<String, Object> row = new LinkedHashMap<>(body);
        row.put("id", id);
        row.put("provId", provId);
        row.put("cityId", cityId);
        row.put("provName", provName);
        row.put("cityName", city.get("cityName"));
        row.put("created", valueOrDefault(row.get("created"), now));
        row.put("updated", now);
        if (operatorId != null) {
            row.put("createId", valueOrDefault(row.get("createId"), operatorId));
            row.put("updateId", operatorId);
        }
        dataStore.put(id, row);
        return true;
    }

    @Override
    public boolean update(Map<String, Object> body, Long operatorId) {
        Long id = toLong(body.get("id"));
        if (id == null) {
            return false;
        }
        Map<String, Object> current = dataStore.get(id);
        if (current == null) {
            return false;
        }
        Long provId = toLong(body.get("provId"));
        Long cityId = toLong(body.get("cityId"));
        if (provId == null || cityId == null) {
            return false;
        }
        Map<String, Object> city = findCity(provId, cityId);
        String provName = findProvinceName(provId);
        if (city == null || provName == null) {
            return false;
        }

        Map<String, Object> merged = new LinkedHashMap<>(current);
        merged.putAll(body);
        merged.put("id", id);
        merged.put("provId", provId);
        merged.put("cityId", cityId);
        merged.put("provName", provName);
        merged.put("cityName", city.get("cityName"));
        merged.put("updated", System.currentTimeMillis());
        if (operatorId != null) {
            merged.put("updateId", operatorId);
        }
        dataStore.put(id, merged);
        return true;
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        boolean removed = false;
        for (Long id : ids) {
            if (id != null && dataStore.remove(id) != null) {
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public List<Map<String, Object>> allProvinces() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> prov : PROVINCES) {
            result.add(copy(prov));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> allCities(Long provId) {
        List<Map<String, Object>> cities = PROV_CITY_MAP.get(provId);
        if (cities == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> city : cities) {
            result.add(copy(city));
        }
        return result;
    }

    private static Map<String, Object> prov(Long id, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("provName", name);
        return map;
    }

    private static Map<String, Object> city(Long id, Long provId, String cityName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("provId", provId);
        map.put("cityName", cityName);
        return map;
    }

    @SafeVarargs
    private static List<Map<String, Object>> cityList(Map<String, Object>... cities) {
        List<Map<String, Object>> list = new ArrayList<>();
        Collections.addAll(list, cities);
        return Collections.unmodifiableList(list);
    }

    private String findProvinceName(Long provId) {
        for (Map<String, Object> prov : PROVINCES) {
            if (provId.equals(toLong(prov.get("id")))) {
                return toStr(prov.get("provName"));
            }
        }
        return null;
    }

    private Map<String, Object> findCity(Long provId, Long cityId) {
        List<Map<String, Object>> cities = PROV_CITY_MAP.get(provId);
        if (cities == null) {
            return null;
        }
        for (Map<String, Object> city : cities) {
            if (cityId.equals(toLong(city.get("id")))) {
                return city;
            }
        }
        return null;
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

    private Object valueOrDefault(Object value, Object defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }
}

