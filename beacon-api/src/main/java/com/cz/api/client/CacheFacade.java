package com.cz.api.client;

import com.cz.common.vo.ResultVO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Typed cache access facade for beacon-api.
 */
@Component
public class CacheFacade {

    private final BeaconCacheClient cacheClient;

    public CacheFacade(BeaconCacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    public Map<String, String> hGetAll(String key) {
        return unwrap(cacheClient.hGetAllTyped(key));
    }

    public String hGetString(String key, String field) {
        return unwrap(cacheClient.hgetStringTyped(key, field));
    }

    public Integer hGetInteger(String key, String field) {
        return unwrap(cacheClient.hgetIntegerTyped(key, field));
    }

    public Set<Map<String, Object>> sMembersMap(String key) {
        return unwrap(cacheClient.smemberTyped(key));
    }

    private <T> T unwrap(ResultVO<T> response) {
        if (response == null) {
            return null;
        }
        Integer code = response.getCode();
        if (code != null && code != 0) {
            throw new IllegalStateException("cache v2 call failed, code=" + code + ", msg=" + response.getMsg());
        }
        return response.getData();
    }
}
