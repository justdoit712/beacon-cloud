package com.cz.webmaster.rebuild.loader;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.mapper.ClientSignMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuild loader for the {@code client_sign} domain.
 */
@Component
public class ClientSignDomainRebuildLoader implements DomainRebuildLoader {

    private final ClientSignMapper clientSignMapper;

    public ClientSignDomainRebuildLoader(ClientSignMapper clientSignMapper) {
        this.clientSignMapper = clientSignMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.CLIENT_SIGN;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<Map<String, Object>> rows = clientSignMapper.findAllActive();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<Map<String, Object>>> membersByClientId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Long clientId = toLong(row.get("clientId"));
            if (clientId == null) {
                continue;
            }

            Map<String, Object> member = new LinkedHashMap<>(row);
            member.remove("clientId");
            membersByClientId.computeIfAbsent(clientId, key -> new ArrayList<>()).add(member);
        }

        List<Object> payloads = new ArrayList<>(membersByClientId.size());
        for (Map.Entry<Long, List<Map<String, Object>>> entry : membersByClientId.entrySet()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", entry.getKey());
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
