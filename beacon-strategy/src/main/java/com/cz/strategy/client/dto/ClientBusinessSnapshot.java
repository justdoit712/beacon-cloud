package com.cz.strategy.client.dto;

/**
 * 客户业务配置快照。
 */
public class ClientBusinessSnapshot {

    private final String apiKey;
    private final Integer isCallback;
    private final String callbackUrl;
    private final String clientFilters;

    public ClientBusinessSnapshot(String apiKey, Integer isCallback, String callbackUrl, String clientFilters) {
        this.apiKey = apiKey;
        this.isCallback = isCallback;
        this.callbackUrl = callbackUrl;
        this.clientFilters = clientFilters;
    }

    public static ClientBusinessSnapshot empty(String apiKey) {
        return new ClientBusinessSnapshot(apiKey, null, null, null);
    }

    public String getApiKey() {
        return apiKey;
    }

    public Integer getIsCallback() {
        return isCallback;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public String getClientFilters() {
        return clientFilters;
    }

    public boolean isCallbackEnabled() {
        return Integer.valueOf(1).equals(isCallback);
    }
}
