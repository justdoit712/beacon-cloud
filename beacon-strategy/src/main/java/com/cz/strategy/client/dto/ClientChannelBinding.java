package com.cz.strategy.client.dto;

/**
 * 客户与通道绑定关系快照。
 */
public class ClientChannelBinding {

    private final Long channelId;
    private final Integer weight;
    private final Integer isAvailable;
    private final String clientChannelNumber;

    public ClientChannelBinding(Long channelId, Integer weight, Integer isAvailable, String clientChannelNumber) {
        this.channelId = channelId;
        this.weight = weight;
        this.isAvailable = isAvailable;
        this.clientChannelNumber = clientChannelNumber;
    }

    public Long getChannelId() {
        return channelId;
    }

    public Integer getWeight() {
        return weight;
    }

    public Integer getIsAvailable() {
        return isAvailable;
    }

    public String getClientChannelNumber() {
        return clientChannelNumber;
    }

    public boolean isAvailableForRoute() {
        return Integer.valueOf(0).equals(isAvailable);
    }
}
