package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.service.SmsManageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SmsManageServiceImpl implements SmsManageService {

    private final ConcurrentMap<Long, Map<String, Object>> dataStore = new ConcurrentHashMap<>();

    @Override
    public String validateForSave(Map<String, Object> body) {
        if (body == null) {
            return "request body is required";
        }
        if (!StringUtils.hasText(toStr(body.get("mobile")))) {
            return "mobile is required";
        }
        if (!StringUtils.hasText(toStr(body.get("content")))) {
            return "content is required";
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
        Long id = toLong(body.get("id"));
        if (id == null) {
            id = IdUtil.getSnowflakeNextId();
        }
        long now = System.currentTimeMillis();
        Map<String, Object> row = new LinkedHashMap<>(body);
        row.put("id", id);
        row.put("created", now);
        row.put("updated", now);
        row.put("status", valueOrDefault(row.get("status"), "queued"));
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
        Map<String, Object> merged = new LinkedHashMap<>(current);
        merged.putAll(body);
        merged.put("id", id);
        merged.put("updated", System.currentTimeMillis());
        if (operatorId != null) {
            merged.put("updateId", operatorId);
        }
        dataStore.put(id, merged);
        return true;
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
}

