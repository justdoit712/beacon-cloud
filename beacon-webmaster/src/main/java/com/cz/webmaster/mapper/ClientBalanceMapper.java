package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.ClientBalance;
import org.apache.ibatis.annotations.Param;

/**
 * {@code client_balance} 表的数据访问接口。
 *
 * <p>该 Mapper 只暴露余额真源所需的最小持久化能力：</p>
 * <p>1. 按 {@code clientId} 查询当前余额真源记录；</p>
 * <p>2. 初始化客户余额记录；</p>
 * <p>3. 通过原子 SQL 执行扣费、充值、调账。</p>
 *
 * <p>有意不提供通用更新方法，避免调用方绕过原子更新约束，重新引入
 * “先读旧余额、再写新余额”的并发风险。</p>
 */
public interface ClientBalanceMapper {

    /**
     * 根据客户 id 查询当前有效余额记录。
     *
     * @param clientId 客户 id，对应 {@code client_business.id}
     * @return 余额记录；未命中时返回 {@code null}
     */
    ClientBalance selectByClientId(@Param("clientId") Long clientId);

    /**
     * 查询全部有效余额记录，用于缓存全量重建。
     *
     * @return 有效余额记录列表
     */
    java.util.List<ClientBalance> selectAllActive();

    /**
     * 初始化客户余额记录。
     *
     * <p>约定一客户一余额记录，调用方应确保 {@code client_id} 唯一约束已由数据库保证。</p>
     *
     * @param id 余额记录主键 id
     * @param clientId 客户 id
     * @param initBalance 初始余额，通常为 {@code 0}
     * @param operatorId 创建人与更新人 id；允许为 {@code null}
     * @return 受影响行数；返回 {@code 1} 表示插入成功
     */
    int insertInitialBalance(@Param("id") Long id,
                             @Param("clientId") Long clientId,
                             @Param("initBalance") Long initBalance,
                             @Param("operatorId") Long operatorId);

    /**
     * 以原子方式执行余额扣费。
     *
     * @param clientId 客户 id
     * @param fee 扣费金额，应为正数
     * @param amountLimit 扣费后允许达到的最低余额
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return 受影响行数；返回 {@code 1} 表示成功，返回 {@code 0} 表示记录不存在、
     * 已删除或扣费后余额会低于 {@code amountLimit}
     */
    int debitBalanceAtomic(@Param("clientId") Long clientId,
                           @Param("fee") Long fee,
                           @Param("amountLimit") Long amountLimit,
                           @Param("updateId") Long updateId);

    /**
     * 以原子方式执行余额充值。
     *
     * @param clientId 客户 id
     * @param amount 充值金额，应为正数
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return 受影响行数；返回 {@code 1} 表示成功，返回 {@code 0} 表示记录不存在或已删除
     */
    int rechargeBalanceAtomic(@Param("clientId") Long clientId,
                              @Param("amount") Long amount,
                              @Param("updateId") Long updateId);

    /**
     * 以原子方式执行余额调账。
     *
     * <p>{@code delta > 0} 表示加款，{@code delta < 0} 表示减款。</p>
     *
     * @param clientId 客户 id
     * @param delta 调账增量
     * @param amountLimit 调账后允许达到的最低余额；为 {@code null} 时不校验下限
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return 受影响行数；返回 {@code 1} 表示成功，返回 {@code 0} 表示记录不存在、
     * 已删除或调账后余额会低于 {@code amountLimit}
     */
    int adjustBalanceAtomic(@Param("clientId") Long clientId,
                            @Param("delta") Long delta,
                            @Param("amountLimit") Long amountLimit,
                            @Param("updateId") Long updateId);
}
