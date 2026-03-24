package com.cz.webmaster.rebuild.loader;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.mapper.ClientChannelMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code client_channel} 域缓存重建加载器。
 */
@Component
public class ClientChannelDomainRebuildLoader implements DomainRebuildLoader {

    private final ClientChannelMapper clientChannelMapper;

    public ClientChannelDomainRebuildLoader(ClientChannelMapper clientChannelMapper) {
        this.clientChannelMapper = clientChannelMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.CLIENT_CHANNEL;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<Long> clientIds = clientChannelMapper.findActiveClientIds();
        if (clientIds == null || clientIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> memberRows = clientChannelMapper.findRouteMembersByClientIds(clientIds);
        Map<Long, List<Map<String, Object>>> membersByClientId = new LinkedHashMap<>();
        for (Long clientId : clientIds) {
            if (clientId != null && clientId > 0) {
                membersByClientId.put(clientId, new ArrayList<>());
            }
        }

        if (memberRows != null) {
            for (Map<String, Object> row : memberRows) {
                if (row == null || row.isEmpty()) {
                    continue;
                }
                Long clientId = toLong(row.get("clientId"));
                if (clientId == null || !membersByClientId.containsKey(clientId)) {
                    continue;
                }
                Map<String, Object> member = new LinkedHashMap<>(row);
                member.remove("clientId");
                membersByClientId.get(clientId).add(member);
            }
        }

        List<Object> payloads = new ArrayList<>(membersByClientId.size());
        for (Map.Entry<Long, List<Map<String, Object>>> entry : membersByClientId.entrySet()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", entry.getKey());
            payload.put("members", entry.getValue() == null ? Collections.emptyList() : entry.getValue());
            payloads.add(payload);
        }
        return payloads;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            long parsed = ((Number) value).longValue();
            return parsed > 0 ? parsed : null;
        }
        if (value == null) {
            return null;
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
