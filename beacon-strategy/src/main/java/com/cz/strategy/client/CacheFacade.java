package com.cz.strategy.client;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.vo.ResultVO;
import com.cz.strategy.client.dto.ChannelInfo;
import com.cz.strategy.client.dto.ClientBusinessSnapshot;
import com.cz.strategy.client.dto.ClientChannelBinding;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strategy 模块使用的强类型缓存访问门面。
 */
@Component
public class CacheFacade {

    private static final long PUSH_REPORT_DEDUP_TTL_SECONDS = 24 * 60 * 60L;
    private static final String PUSH_REPORT_DEDUP_KEY_PREFIX = "strategy:push_report_dedup:";

    private final BeaconCacheClient cacheClient;

    public CacheFacade(BeaconCacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    public String hGetString(String key, String field) {
        return unwrap(cacheClient.hgetTyped(key, field));
    }

    public Integer hGetInteger(String key, String field) {
        return unwrap(cacheClient.hgetIntegerTyped(key, field));
    }

    public String getString(String key) {
        return unwrap(cacheClient.getStringTyped(key));
    }

    public Set<String> sMembersString(String key) {
        Set<String> values = unwrap(cacheClient.smemberTyped(key));
        return values == null ? Collections.emptySet() : values;
    }

    public String getClientFilters(String apiKey) {
        return hGetString(CacheKeyConstants.CLIENT_BUSINESS + apiKey, "clientFilters");
    }

    public ClientBusinessSnapshot getClientBusinessSnapshot(String apiKey) {
        Map<String, String> values = unwrap(cacheClient.hGetAllTyped(CacheKeyConstants.CLIENT_BUSINESS + apiKey));
        if (values == null || values.isEmpty()) {
            return ClientBusinessSnapshot.empty(apiKey);
        }
        return new ClientBusinessSnapshot(
                apiKey,
                toInteger(values.get(CacheKeyConstants.IS_CALLBACK), CacheKeyConstants.IS_CALLBACK),
                trimToNull(values.get(CacheKeyConstants.CALLBACK_URL)),
                trimToNull(values.get("clientFilters"))
        );
    }

    public List<ClientChannelBinding> getClientChannelBindings(Long clientId) {
        Set<Map<String, Object>> values = unwrap(cacheClient.smemberMapTyped(CacheKeyConstants.CLIENT_CHANNEL + clientId));
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClientChannelBinding> bindings = new ArrayList<>();
        for (Map<String, Object> value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            Long channelId = toLong(value.get("channelId"), "channelId");
            if (channelId == null) {
                continue;
            }
            bindings.add(new ClientChannelBinding(
                    channelId,
                    defaultInteger(toInteger(value.get("clientChannelWeight"), "clientChannelWeight"), 0),
                    defaultInteger(toInteger(value.get("isAvailable"), "isAvailable"), 1),
                    trimToNull(value.get("clientChannelNumber") == null ? null : String.valueOf(value.get("clientChannelNumber")))
            ));
        }
        return bindings;
    }

    public ChannelInfo getChannelInfo(Long channelId) {
        Map<String, String> values = unwrap(cacheClient.hGetAllTyped(CacheKeyConstants.CHANNEL + channelId));
        if (values == null || values.isEmpty()) {
            return null;
        }
        return new ChannelInfo(
                toLong(values.get("id"), "id"),
                toInteger(values.get("isAvailable"), "isAvailable"),
                toInteger(values.get("channelType"), "channelType"),
                trimToNull(values.get("channelNumber"))
        );
    }

    public boolean markPushReportDispatched(Long sequenceId) {
        if (sequenceId == null) {
            return true;
        }
        Boolean value = cacheClient.setIfAbsent(
                PUSH_REPORT_DEDUP_KEY_PREFIX + sequenceId,
                "1",
                PUSH_REPORT_DEDUP_TTL_SECONDS
        );
        return value == null || value;
    }

    private static <T> T unwrap(ResultVO<T> response) {
        return BeaconCacheClient.unwrap(response);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static Integer toInteger(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("invalid integer value for field " + fieldName + ": " + value, ex);
        }
    }

    private static Long toLong(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("invalid long value for field " + fieldName + ": " + value, ex);
        }
    }

    private static Integer defaultInteger(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
