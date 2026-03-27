package com.cz.webmaster.rebuild.loader;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.MobileDirtyWord;
import com.cz.webmaster.mapper.MobileDirtyWordMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Rebuild loader for the {@code dirty_word} domain.
 */
@Component
public class DirtyWordDomainRebuildLoader implements DomainRebuildLoader {

    private final MobileDirtyWordMapper mobileDirtyWordMapper;

    public DirtyWordDomainRebuildLoader(MobileDirtyWordMapper mobileDirtyWordMapper) {
        this.mobileDirtyWordMapper = mobileDirtyWordMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.DIRTY_WORD;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<MobileDirtyWord> rows = mobileDirtyWordMapper.findAllActive();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> members = new LinkedHashSet<>();
        for (MobileDirtyWord row : rows) {
            if (row == null || !StringUtils.hasText(row.getDirtyword())) {
                continue;
            }
            members.add(row.getDirtyword().trim());
        }
        if (members.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("members", new ArrayList<>(members));
        List<Object> payloads = new ArrayList<>();
        payloads.add(payload);
        return payloads;
    }
}
