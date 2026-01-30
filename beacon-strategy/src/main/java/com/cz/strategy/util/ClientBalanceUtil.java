package com.cz.strategy.util;

public class ClientBalanceUtil {


    /**
     * 获取客户余额限制
     * @param clientId
     * @return
     */
    public static Long getClientAmountLimit(Long clientId) {
        return -10000L;
    }
}
