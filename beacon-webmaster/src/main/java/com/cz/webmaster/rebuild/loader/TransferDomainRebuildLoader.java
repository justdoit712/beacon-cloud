package com.cz.webmaster.rebuild.loader;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.MobileTransfer;
import com.cz.webmaster.mapper.MobileTransferMapper;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuild loader for the {@code transfer} domain.
 */
@Component
public class TransferDomainRebuildLoader implements DomainRebuildLoader {

    private final MobileTransferMapper mobileTransferMapper;

    public TransferDomainRebuildLoader(MobileTransferMapper mobileTransferMapper) {
        this.mobileTransferMapper = mobileTransferMapper;
    }

    @Override
    public String domainCode() {
        return CacheDomainRegistry.TRANSFER;
    }

    @Override
    public List<Object> loadSnapshot() {
        List<MobileTransfer> rows = mobileTransferMapper.findAllActive();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> payloads = new ArrayList<>();
        for (MobileTransfer row : rows) {
            if (row == null || !StringUtils.hasText(row.getTransferNumber()) || row.getNowIsp() == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mobile", row.getTransferNumber().trim());
            payload.put("value", String.valueOf(row.getNowIsp()));
            payloads.add(payload);
        }
        return payloads;
    }
}
