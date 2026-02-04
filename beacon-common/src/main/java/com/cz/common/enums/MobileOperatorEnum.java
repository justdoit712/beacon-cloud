package com.cz.common.enums;

import lombok.Getter;

@Getter
public enum MobileOperatorEnum {

    CHINA_MOBILE(1, "移动"),
    CHINA_UNICOM(2, "联通"),
    CHINA_TELECOM(3, "电信"),
    UNKNOW(0, "未知");

    private Integer operatorId;

    private String operatorName;

    MobileOperatorEnum(Integer operatorId, String operatorName) {
        this.operatorId = operatorId;
        this.operatorName = operatorName;
    }
}
