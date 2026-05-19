package com.cz.webmaster.vo;

public class MobileBlackVO {

    private Long id;
    private String mobile;
    private Integer owntype;
    private String creater;
    private Integer clientId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Integer getOwntype() {
        return owntype;
    }

    public void setOwntype(Integer owntype) {
        this.owntype = owntype;
    }

    public String getCreater() {
        return creater;
    }

    public void setCreater(String creater) {
        this.creater = creater;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }
}
