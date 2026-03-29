package com.cz.smsgateway.client;

import com.cz.common.constant.CacheKeyConstants;
import org.springframework.stereotype.Component;

/**
 * Typed cache access facade for smsgateway.
 */
@Component
public class CacheFacade {

    private final BeaconCacheClient cacheClient;

    public CacheFacade(BeaconCacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    public boolean isClientCallbackEnabled(String apiKey) {
        Integer isCallback = cacheClient.hgetInteger(clientBusinessKey(apiKey), CacheKeyConstants.IS_CALLBACK);
        return Integer.valueOf(1).equals(isCallback);
    }

    public String getClientCallbackUrl(String apiKey) {
        return cacheClient.hget(clientBusinessKey(apiKey), CacheKeyConstants.CALLBACK_URL);
    }

    private String clientBusinessKey(String apiKey) {
        return CacheKeyConstants.CLIENT_BUSINESS + apiKey;
    }
}
