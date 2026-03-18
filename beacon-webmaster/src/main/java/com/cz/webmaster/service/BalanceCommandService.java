package com.cz.webmaster.service;

import com.cz.webmaster.dto.BalanceCommandResult;

/**
 * 统一余额命令服务接口。
 *
 * <p>该接口用于收口所有会修改客户余额真源的业务命令。
 * 当前统一抽象为扣费、充值和调账三类入口，
 * 调用方不应再自行通过“先查旧余额、再拼新余额、最后更新”的方式修改余额。</p>
 *
 * <p>所有余额命令都应遵循同一套处理规则：</p>
 * <p>1. 以 MySQL {@code client_business.extend4} 作为余额真源；</p>
 * <p>2. 通过原子 SQL 执行余额变更；</p>
 * <p>3. 真源提交成功后，再刷新余额相关缓存；</p>
 * <p>4. 使用统一的结果对象返回业务执行结果。</p>
 */
public interface BalanceCommandService {

    /**
     * 执行一次客户余额扣费，并在成功后触发相关缓存刷新。
     *
     * @param clientId 客户 id，必须为正数
     * @param fee 扣费金额，必须为正数
     * @param amountLimit 本次扣费允许达到的最低余额；允许为 {@code null}
     * @param requestId 请求标识，用于日志串联；允许为 {@code null}
     * @return 余额命令执行结果
     */
    BalanceCommandResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId);

    /**
     * 执行一次客户余额充值，并在成功后触发相关缓存刷新。
     *
     * @param clientId 客户 id，必须为正数
     * @param amount 充值金额，必须为正数
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @param requestId 请求标识，用于日志串联；允许为 {@code null}
     * @return 余额命令执行结果
     */
    BalanceCommandResult rechargeAndSync(Long clientId, Long amount, Long updateId, String requestId);

    /**
     * 执行一次客户余额调账，并在成功后触发相关缓存刷新。
     *
     * <p>{@code delta} 为正数表示加款，为负数表示减款。</p>
     *
     * @param clientId 客户 id，必须为正数
     * @param delta 调账增量；正数表示加款，负数表示减款
     * @param amountLimit 本次调账允许达到的最低余额；允许为 {@code null}
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @param requestId 请求标识，用于日志串联；允许为 {@code null}
     * @return 余额命令执行结果
     */
    BalanceCommandResult adjustAndSync(Long clientId, Long delta, Long amountLimit, Long updateId, String requestId);
}
