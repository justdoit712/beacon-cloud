package com.cz.webmaster.service;

import com.cz.webmaster.dto.BalanceCommandResult;

/**
 * 客户余额扣费服务。
 *
 * <p>该接口用于执行客户余额扣费：
 * 先更新 MySQL 真源，再由实现类在事务提交后刷新余额缓存。</p>
 *
 * <p>当前接口只暴露扣费能力；如果后续增加充值、调账等余额变更能力，
 * 也应遵循同一套“先更新真源、后刷新缓存”的规则。</p>
 */
public interface ClientBalanceDebitService {

    /**
     * 执行一次客户余额扣费，并在成功后触发余额缓存刷新。
     *
     * <p>实现类应保证以下语义：</p>
     * <p>1. 扣费写入以 MySQL 真源为准；</p>
     * <p>2. 扣费操作应尽量通过原子 SQL 完成，避免“先读后写”；</p>
     * <p>3. 缓存刷新应在事务提交成功后执行；</p>
     * <p>4. 返回值应能明确表达成功、失败和失败原因。</p>
     *
     * @param clientId 客户 id，必须为正数
     * @param fee 扣费金额，必须为正数
     * @param amountLimit 余额允许下限；为 {@code null} 时由实现类使用默认值
     * @param requestId 请求标识，用于日志串联；允许为 {@code null}
     * @return 扣费结果对象
     * @throws IllegalArgumentException 当参数非法时抛出
     */
    BalanceCommandResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId);
}
