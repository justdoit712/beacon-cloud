package com.cz.test.entity;

import java.io.Serializable;

/**
 * 手机号黑名单 实体类
 */
public class MobileBlack implements Serializable {

    private static final long serialVersionUID = 1L;

    private String blackNumber;
    private Integer clientId;

    // 无参构造函数
    public MobileBlack() {
    }

    // 全参构造函数
    public MobileBlack(String blackNumber, Integer clientId) {
        this.blackNumber = blackNumber;
        this.clientId = clientId;
    }

    // Getter 和 Setter 方法
    public String getBlackNumber() {
        return blackNumber;
    }

    public void setBlackNumber(String blackNumber) {
        this.blackNumber = blackNumber;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    // 重写 toString，方便排查黑名单匹配逻辑
    @Override
    public String toString() {
        return "MobileBlack{" +
                "blackNumber='" + blackNumber + '\'' +
                ", clientId=" + clientId +
                '}';
    }
}