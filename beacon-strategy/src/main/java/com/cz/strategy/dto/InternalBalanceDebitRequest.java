package com.cz.strategy.dto;

import lombok.Data;

/**
 * 调用 webmaster 内部余额扣减接口的请求体。
 */
@Data
public class InternalBalanceDebitRequest {

    private Long clientId;

    private Long fee;

    private Long amountLimit;

    private String requestId;
}
