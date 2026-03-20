package com.cz.webmaster.service;

import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceAdjustCommand;
import com.cz.webmaster.dto.ClientBalanceDebitCommand;
import com.cz.webmaster.dto.ClientBalanceRechargeCommand;

/**
 * 统一余额命令服务。
 *
 * <p>该接口以 {@code client_balance} 作为余额真源的统一服务入口，
 * 负责承接扣费、充值、调账三类余额命令。</p>
 *
 * <p>命令对象方法是当前规范入口；
 * 标量重载方法仅作为兼容包装，便于旧调用方平滑迁移。</p>
 */
public interface BalanceCommandService {

    /**
     * 执行扣费命令。
     *
     * @param command 扣费命令
     * @return 命令执行结果
     */
    BalanceCommandResult debitAndSync(ClientBalanceDebitCommand command);

    /**
     * 执行充值命令。
     *
     * @param command 充值命令
     * @return 命令执行结果
     */
    BalanceCommandResult rechargeAndSync(ClientBalanceRechargeCommand command);

    /**
     * 执行调账命令。
     *
     * @param command 调账命令
     * @return 命令执行结果
     */
    BalanceCommandResult adjustAndSync(ClientBalanceAdjustCommand command);

    /**
     * 兼容旧调用方的扣费入口。
     *
     * <p>内部会将标量参数组装为 {@link ClientBalanceDebitCommand} 再统一委托。</p>
     */
    default BalanceCommandResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId) {
        ClientBalanceDebitCommand command = new ClientBalanceDebitCommand();
        command.setClientId(clientId);
        command.setFee(fee);
        command.setAmountLimit(amountLimit);
        command.setRequestId(requestId);
        return debitAndSync(command);
    }

    /**
     * 兼容旧调用方的充值入口。
     *
     * <p>内部会将标量参数组装为 {@link ClientBalanceRechargeCommand} 再统一委托。</p>
     */
    default BalanceCommandResult rechargeAndSync(Long clientId, Long amount, Long updateId, String requestId) {
        ClientBalanceRechargeCommand command = new ClientBalanceRechargeCommand();
        command.setClientId(clientId);
        command.setAmount(amount);
        command.setOperatorId(updateId);
        command.setRequestId(requestId);
        return rechargeAndSync(command);
    }

    /**
     * 兼容旧调用方的调账入口。
     *
     * <p>内部会将标量参数组装为 {@link ClientBalanceAdjustCommand} 再统一委托。</p>
     */
    default BalanceCommandResult adjustAndSync(Long clientId, Long delta, Long amountLimit, Long updateId, String requestId) {
        ClientBalanceAdjustCommand command = new ClientBalanceAdjustCommand();
        command.setClientId(clientId);
        command.setDelta(delta);
        command.setAmountLimit(amountLimit);
        command.setOperatorId(updateId);
        command.setRequestId(requestId);
        return adjustAndSync(command);
    }
}
