package com.cz.webmaster.dto;

/**
 * 客户余额调账命令。
 */
public class ClientBalanceAdjustCommand {

    private Long clientId;
    private Long delta;
    private Long amountLimit;
    private Long operatorId;
    private String requestId;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getDelta() {
        return delta;
    }

    public void setDelta(Long delta) {
        this.delta = delta;
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
