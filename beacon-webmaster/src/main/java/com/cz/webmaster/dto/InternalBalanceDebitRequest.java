package com.cz.webmaster.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class InternalBalanceDebitRequest {

    @NotNull(message = "clientId can not be null")
    @Min(value = 1, message = "clientId must be positive")
    private Long clientId;

    @NotNull(message = "fee can not be null")
    @Min(value = 1, message = "fee must be positive")
    private Long fee;

    private Long amountLimit;

    private String requestId;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getFee() {
        return fee;
    }

    public void setFee(Long fee) {
        this.fee = fee;
    }

    public Long getAmountLimit() {
        return amountLimit;
    }

    public void setAmountLimit(Long amountLimit) {
        this.amountLimit = amountLimit;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}

