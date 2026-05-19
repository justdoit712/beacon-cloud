package com.cz.webmaster.vo;

public class CodeLimitVO {

    private Long id;
    private Integer limitTime;
    private Integer limitCount;
    private String despcription;
    private Integer limitState;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getLimitTime() {
        return limitTime;
    }

    public void setLimitTime(Integer limitTime) {
        this.limitTime = limitTime;
    }

    public Integer getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(Integer limitCount) {
        this.limitCount = limitCount;
    }

    public String getDespcription() {
        return despcription;
    }

    public void setDespcription(String despcription) {
        this.despcription = despcription;
    }

    public Integer getLimitState() {
        return limitState;
    }

    public void setLimitState(Integer limitState) {
        this.limitState = limitState;
    }
}
