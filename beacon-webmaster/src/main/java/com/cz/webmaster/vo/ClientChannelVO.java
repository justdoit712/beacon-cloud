package com.cz.webmaster.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class ClientChannelVO {

    private Long id;

    @JsonProperty("clientid")
    private Long clientId;

    @JsonProperty("channelid")
    private Long channelId;

    @JsonProperty("extendnumber")
    private String extendNumber;

    private Long price;

    @JsonProperty("corpname")
    private String corpName;

    @JsonProperty("channelname")
    private String channelName;

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getExtendNumber() {
        return extendNumber;
    }

    public void setExtendNumber(String extendNumber) {
        this.extendNumber = extendNumber;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public String getCorpName() {
        return corpName;
    }

    public void setCorpName(String corpName) {
        this.corpName = corpName;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }
}
