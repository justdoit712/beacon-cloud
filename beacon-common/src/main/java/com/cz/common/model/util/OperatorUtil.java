package com.cz.common.model.util;

import com.cz.common.model.enums.MobileOperatorEnum;

import java.util.HashMap;
import java.util.Map;

public class OperatorUtil {

    private static Map<String, Integer> operators = new HashMap<>();

    static {
        MobileOperatorEnum[] operatorEnums = MobileOperatorEnum.values();
        for (MobileOperatorEnum operatorEnum : operatorEnums) {
            operators.put(operatorEnum.getOperatorName(), operatorEnum.getOperatorId());
        }
    }

    /**
     * 根据运营商名称获取运营商ID
     *
     * @param operatorName
     * @return
     */
    public static Integer getOperatorIdByOperatorName(String operatorName) {
        return operators.get(operatorName);
    }
}
