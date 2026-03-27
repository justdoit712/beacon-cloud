package com.cz.webmaster.rebuild.loader;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.MobileBlack;
import com.cz.webmaster.mapper.MobileBlackMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuild loader for the {@code black} domain.
 */
@Component
public class BlackDomainRebuildLoader implements DomainRebuildLoader {

    private final MobileBlackMapper mobileBlackMapper;

    public BlackDomainRebuildLoader(MobileBlackMapper mobileBlackMapper) {
        this.mobileBlackMapper = mobileBlackMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.BLACK;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<MobileBlack> rows = mobileBlackMapper.findAllActive();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> payloads = new ArrayList<>();
        for (MobileBlack row : rows) {
            if (row == null) {
                continue;
            }
            String mobile = toText(row.getBlackNumber());
            if (mobile == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mobile", mobile);
            Integer clientId = row.getClientId();
            if (clientId != null && clientId > 0) {
                payload.put("clientId", clientId);
            }
            payloads.add(payload);
        }
        return payloads;
    }

    private String toText(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String text = String.valueOf(candidate).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }
}
