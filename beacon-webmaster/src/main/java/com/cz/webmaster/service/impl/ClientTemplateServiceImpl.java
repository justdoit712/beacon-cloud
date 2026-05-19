package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.mapper.ClientTemplateMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.ClientTemplateService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ClientTemplateServiceImpl implements ClientTemplateService {

    private final ClientTemplateMapper clientTemplateMapper;
    private final CacheSyncService cacheSyncService;
    private final CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    public ClientTemplateServiceImpl(ClientTemplateMapper clientTemplateMapper,
                                     CacheSyncService cacheSyncService,
                                     CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor) {
        this.clientTemplateMapper = clientTemplateMapper;
        this.cacheSyncService = cacheSyncService;
        this.cacheSyncRuntimeExecutor = cacheSyncRuntimeExecutor;
    }

    @Override
    public long countByKeyword(String keyword) {
        return clientTemplateMapper.countByKeyword(normalizeKeyword(keyword));
    }

    @Override
    public List<Map<String, Object>> findPage(String keyword, int offset, int limit) {
        return clientTemplateMapper.findPage(normalizeKeyword(keyword), offset, limit);
    }

    @Override
    public Map<String, Object> findById(Long id) {
        if (id == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> row = clientTemplateMapper.findById(id);
        return row == null ? new LinkedHashMap<>() : row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Map<String, Object> body, Long operatorId) {
        Map<String, Object> row = toRow(body, operatorId, true);
        if (row.isEmpty()) {
            return false;
        }
        boolean saved = clientTemplateMapper.insert(row) > 0;
        if (!saved) {
            return false;
        }
        syncSignTemplate(toLong(row.get("signId")), "upsert", toLong(row.get("id")));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Map<String, Object> body, Long operatorId) {
        Long id = toLong(readValue(body, "id"));
        if (id == null) {
            return false;
        }
        Map<String, Object> before = clientTemplateMapper.findById(id);
        if (before == null || before.isEmpty()) {
            return false;
        }

        Map<String, Object> row = toRow(body, operatorId, false);
        row.put("id", id);
        boolean updated = clientTemplateMapper.update(row) > 0;
        if (!updated) {
            return false;
        }

        Long beforeSignId = toLong(before.get("signId"));
        Long afterSignId = toLong(row.get("signId"));
        if (beforeSignId != null && !Objects.equals(beforeSignId, afterSignId)) {
            syncSignTemplate(beforeSignId, "upsert.oldSign", id);
        }
        syncSignTemplate(afterSignId, "upsert", id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long operatorId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }

        Set<Long> signIds = new LinkedHashSet<>();
        List<Long> deletedIds = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            Map<String, Object> existing = clientTemplateMapper.findById(id);
            if (existing == null || existing.isEmpty()) {
                return false;
            }
            if (clientTemplateMapper.logicalDelete(id, operatorId) <= 0) {
                return false;
            }
            Long signId = toLong(existing.get("signId"));
            if (signId != null) {
                signIds.add(signId);
            }
            deletedIds.add(id);
        }

        if (deletedIds.isEmpty()) {
            return false;
        }
        for (Long signId : signIds) {
            syncSignTemplate(signId, "upsert.afterDelete", signId);
        }
        return true;
    }

    private void syncSignTemplate(Long signId, String operation, Long entityId) {
        if (signId == null || signId <= 0) {
            return;
        }
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("signId", signId);
                    payload.put("members", clientTemplateMapper.findActiveMembersBySignId(signId));
                    cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_TEMPLATE, payload);
                },
                CacheDomainRegistry.CLIENT_TEMPLATE,
                operation,
                safeEntityId(entityId)
        );
    }

    private Map<String, Object> toRow(Map<String, Object> body, Long operatorId, boolean create) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) {
            return row;
        }

        Long id = toLong(readValue(body, "id"));
        if (create && id == null) {
            id = IdUtil.getSnowflakeNextId();
        }
        row.put("id", id);
        row.put("signId", toLong(readValue(body, "signId", "sign_id")));
        row.put("templateText", readText(body, "templateText", "template_text", "template"));
        row.put("templateType", toInteger(readValue(body, "templateType", "template_type")));
        row.put("templateState", toInteger(readValue(body, "templateState", "template_state", "status")));
        row.put("useId", toInteger(readValue(body, "useId", "use_id")));
        row.put("useWeb", readText(body, "useWeb", "use_web"));
        row.put("extend1", readText(body, "extend1"));
        row.put("extend2", readText(body, "extend2"));
        row.put("extend3", readText(body, "extend3"));
        row.put("extend4", readText(body, "extend4"));
        row.put("updateId", operatorId);
        if (create) {
            row.put("createId", operatorId);
            row.put("isDelete", 0);
        }
        return row;
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }

    private Object readValue(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private String readText(Map<String, Object> row, String... keys) {
        Object value = readValue(row, keys);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long parsed = ((Number) value).longValue();
            return parsed > 0 ? parsed : null;
        }
        try {
            long parsed = Long.parseLong(value.toString());
            return parsed > 0 ? parsed : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignore) {
            return null;
        }
    }
}
