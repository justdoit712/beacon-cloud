package com.cz.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum MobileOperatorEnum {

    CHINA_MOBILE(1, "移动"),
    CHINA_UNICOM(2, "联通"),
    CHINA_TELECOM(3, "电信"),
    UNKNOW(0, "未知");

    private Integer operatorId;

    private String operatorName;

    private static final Map<String, MobileOperatorEnum> BY_NAME = Collections.unmodifiableMap(
            Arrays.stream(values()).collect(Collectors.toMap(MobileOperatorEnum::getOperatorName, Function.identity()))
    );

    MobileOperatorEnum(Integer operatorId, String operatorName) {
        this.operatorId = operatorId;
        this.operatorName = operatorName;
    }

    public static Integer operatorIdByName(String operatorName) {
        MobileOperatorEnum operator = BY_NAME.get(operatorName);
        return operator == null ? null : operator.operatorId;
    }
}
