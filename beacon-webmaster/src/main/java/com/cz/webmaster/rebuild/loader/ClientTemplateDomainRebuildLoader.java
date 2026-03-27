package com.cz.webmaster.rebuild.loader;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.mapper.ClientTemplateMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuild loader for the {@code client_template} domain.
 */
@Component
public class ClientTemplateDomainRebuildLoader implements DomainRebuildLoader {

    private final ClientTemplateMapper clientTemplateMapper;

    public ClientTemplateDomainRebuildLoader(ClientTemplateMapper clientTemplateMapper) {
        this.clientTemplateMapper = clientTemplateMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.CLIENT_TEMPLATE;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<Map<String, Object>> rows = clientTemplateMapper.findAllActive();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<Map<String, Object>>> membersBySignId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Long signId = toLong(row.get("signId"));
            if (signId == null) {
                continue;
            }

            Map<String, Object> member = new LinkedHashMap<>(row);
            member.remove("signId");
            membersBySignId.computeIfAbsent(signId, key -> new ArrayList<>()).add(member);
        }

        List<Object> payloads = new ArrayList<>(membersBySignId.size());
        for (Map.Entry<Long, List<Map<String, Object>>> entry : membersBySignId.entrySet()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("signId", entry.getKey());
            payload.put("members", entry.getValue());
            payloads.add(payload);
        }
        return payloads;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long parsed = ((Number) value).longValue();
            return parsed > 0 ? parsed : null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(text);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
