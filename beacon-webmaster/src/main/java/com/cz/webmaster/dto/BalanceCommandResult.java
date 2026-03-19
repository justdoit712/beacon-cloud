package com.cz.webmaster.dto;

import com.cz.webmaster.enums.BalanceCommandStatus;

/**
 * 余额命令执行结果。
 *
 * <p>该对象用于承载余额相关命令在 Service 层的执行结果，
 * 当前先覆盖扣费场景，后续充值、调账等余额命令也可继续复用。</p>
 */
public class BalanceCommandResult {

    /**
     * 本次命令的执行状态。
     */
    private final BalanceCommandStatus status;

    /**
     * 命令执行后的最新余额。
     *
     * <p>失败场景下允许为 {@code null}。</p>
     */
    private final Long balance;

    /**
     * 本次命令执行时生效的余额下限。
     */
    private final Long amountLimit;

    /**
     * 构造余额命令结果。
     *
     * @param status 执行状态
     * @param balance 最新余额；失败时允许为 {@code null}
     * @param amountLimit 生效的余额下限
     */
    public BalanceCommandResult(BalanceCommandStatus status, Long balance, Long amountLimit) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.balance = balance;
        this.amountLimit = amountLimit;
    }

    /**
     * 构造成功结果。
     *
     * @param balance 最新余额
     * @param amountLimit 生效的余额下限
     * @return 成功结果
     */
    public static BalanceCommandResult success(Long balance, Long amountLimit) {
        return new BalanceCommandResult(BalanceCommandStatus.SUCCESS, balance, amountLimit);
    }

    /**
     * 构造失败结果。
     *
     * @param status 失败状态
     * @param amountLimit 生效的余额下限
     * @return 失败结果
     */
    public static BalanceCommandResult failure(BalanceCommandStatus status, Long amountLimit) {
        return new BalanceCommandResult(status, null, amountLimit);
    }

    /**
     * 返回执行状态。
     *
     * @return 执行状态
     */
    public BalanceCommandStatus getStatus() {
        return status;
    }

    /**
     * 返回是否执行成功。
     *
     * @return {@code true} 表示成功，{@code false} 表示失败
     */
    public boolean isSuccess() {
        return status.isSuccess();
    }

    /**
     * 返回状态码。
     *
     * @return 状态码
     */
    public int getCode() {
        return status.getCode();
    }

    /**
     * 返回状态消息。
     *
     * @return 状态消息
     */
    public String getMessage() {
        return status.getMessage();
    }

    /**
     * 返回最新余额。
     *
     * @return 最新余额；失败时可能为 {@code null}
     */
    public Long getBalance() {
        return balance;
    }

    /**
     * 返回本次执行生效的余额下限。
     *
     * @return 生效的余额下限
     */
    public Long getAmountLimit() {
        return amountLimit;
    }
}
