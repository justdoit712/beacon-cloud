package com.cz.webmaster.entity;

import java.util.Date;

/**
 * 短信通道实体
 */
public class Channel {

    private Long id;
    private String channelName;
    private Integer channelType;
    private String channelArea;
    private String channelAreaCode;
    private Long channelPrice;
    private String channelIp;
    private Integer channelPort;
    private String channelUsername;
    private String channelPassword;
    private String spNumber;
    private Integer protocolType;
    private Byte isAvailable;
    private Date created;
    private Long createId;
    private Date updated;
    private Long updateId;
    private Byte isDelete;

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
        this.channelName = channelName == null ? null : channelName.trim();
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
        this.channelArea = channelArea == null ? null : channelArea.trim();
    }

    public String getChannelAreaCode() {
        return channelAreaCode;
    }

    public void setChannelAreaCode(String channelAreaCode) {
        this.channelAreaCode = channelAreaCode == null ? null : channelAreaCode.trim();
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
        this.channelIp = channelIp == null ? null : channelIp.trim();
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
        this.channelUsername = channelUsername == null ? null : channelUsername.trim();
    }

    public String getChannelPassword() {
        return channelPassword;
    }

    public void setChannelPassword(String channelPassword) {
        this.channelPassword = channelPassword == null ? null : channelPassword.trim();
    }

    public String getSpNumber() {
        return spNumber;
    }

    public void setSpNumber(String spNumber) {
        this.spNumber = spNumber == null ? null : spNumber.trim();
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

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Long getCreateId() {
        return createId;
    }

    public void setCreateId(Long createId) {
        this.createId = createId;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(Long updateId) {
        this.updateId = updateId;
    }

    public Byte getIsDelete() {
        return isDelete;
    }

    public void setIsDelete(Byte isDelete) {
        this.isDelete = isDelete;
    }
}