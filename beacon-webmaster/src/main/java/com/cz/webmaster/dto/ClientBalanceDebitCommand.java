package com.cz.webmaster.dto;

/**
 * 客户余额扣费命令。
 */
public class ClientBalanceDebitCommand {

    private Long clientId;
    private Long fee;
    private Long amountLimit;
    private Long operatorId;
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

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
