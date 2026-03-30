package com.cz.strategy.client.dto;

/**
 * 通道缓存快照。
 */
public class ChannelInfo {

    private final Long id;
    private final Integer isAvailable;
    private final Integer channelType;
    private final String channelNumber;

    public ChannelInfo(Long id, Integer isAvailable, Integer channelType, String channelNumber) {
        this.id = id;
        this.isAvailable = isAvailable;
        this.channelType = channelType;
        this.channelNumber = channelNumber;
    }

    public Long getId() {
        return id;
    }

    public Integer getIsAvailable() {
        return isAvailable;
    }

    public Integer getChannelType() {
        return channelType;
    }

    public String getChannelNumber() {
        return channelNumber;
    }

    public boolean isAvailableForRoute() {
        return Integer.valueOf(0).equals(isAvailable);
    }

    public boolean supportsOperator(Integer operatorId) {
        if (Integer.valueOf(0).equals(channelType)) {
            return true;
        }
        return operatorId != null && operatorId.equals(channelType);
    }
}
