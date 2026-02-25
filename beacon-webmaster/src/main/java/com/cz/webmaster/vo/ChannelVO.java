package com.cz.webmaster.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChannelVO {

    private Long id;

    @JsonProperty("channelname")
    private String channelName;

    @JsonProperty("channeltype")
    private Integer channelType;

    @JsonProperty("channelarea")
    private String channelArea;

    @JsonProperty("channelareacode")
    private String channelAreaCode;

    @JsonProperty("channelprice")
    private Long channelPrice;

    @JsonProperty("channelip")
    private String channelIp;

    @JsonProperty("channelport")
    private Integer channelPort;

    @JsonProperty("channelusername")
    private String channelUsername;

    @JsonProperty("channelpassword")
    private String channelPassword;

    @JsonProperty("spnumber")
    private String spNumber;

    @JsonProperty("protocaltype")
    private Integer protocolType;

    @JsonProperty("isavailable")
    private Byte isAvailable;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Integer getChannelType() {
        return channelType;
    }

    public void setChannelType(Integer channelType) {
        this.channelType = channelType;
    }

    public String getChannelArea() {
        return channelArea;
    }

    public void setChannelArea(String channelArea) {
        this.channelArea = channelArea;
    }

    public String getChannelAreaCode() {
        return channelAreaCode;
    }

    public void setChannelAreaCode(String channelAreaCode) {
        this.channelAreaCode = channelAreaCode;
    }

    public Long getChannelPrice() {
        return channelPrice;
    }

    public void setChannelPrice(Long channelPrice) {
        this.channelPrice = channelPrice;
    }

    public String getChannelIp() {
        return channelIp;
    }

    public void setChannelIp(String channelIp) {
        this.channelIp = channelIp;
    }

    public Integer getChannelPort() {
        return channelPort;
    }

    public void setChannelPort(Integer channelPort) {
        this.channelPort = channelPort;
    }

    public String getChannelUsername() {
        return channelUsername;
    }

    public void setChannelUsername(String channelUsername) {
        this.channelUsername = channelUsername;
    }

    public String getChannelPassword() {
        return channelPassword;
    }

    public void setChannelPassword(String channelPassword) {
        this.channelPassword = channelPassword;
    }

    public String getSpNumber() {
        return spNumber;
    }

    public void setSpNumber(String spNumber) {
        this.spNumber = spNumber;
    }

    public Integer getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(Integer protocolType) {
        this.protocolType = protocolType;
    }

    public Byte getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Byte isAvailable) {
        this.isAvailable = isAvailable;
    }
}
