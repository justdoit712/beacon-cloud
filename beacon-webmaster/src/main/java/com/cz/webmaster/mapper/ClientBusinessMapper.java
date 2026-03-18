package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.ClientBusinessExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code client_business} 表的数据访问接口。
 *
 * <p>该 Mapper 负责客户业务主表的基础 CRUD 操作，以及余额真源
 * {@code extend4} 的原子化更新。</p>
 *
 * <p>其中余额相关方法有两个重要约束：</p>
 * <p>1. 余额真源以 MySQL {@code client_business.extend4} 为准；</p>
 * <p>2. 余额变更必须通过原子 SQL 完成，避免“先查后写”在并发场景下造成丢更新。</p>
 */
public interface ClientBusinessMapper {
    long countByExample(ClientBusinessExample example);

    int deleteByExample(ClientBusinessExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ClientBusiness row);

    int insertSelective(ClientBusiness row);

    List<ClientBusiness> selectByExample(ClientBusinessExample example);

    ClientBusiness selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ClientBusiness row, @Param("example") ClientBusinessExample example);

    int updateByExample(@Param("row") ClientBusiness row, @Param("example") ClientBusinessExample example);

    int updateByPrimaryKeySelective(ClientBusiness row);

    int updateByPrimaryKey(ClientBusiness row);

    /**
     * 以原子方式执行余额扣费。
     *
     * <p>该方法直接在数据库侧完成“余额减去扣费金额”的操作，
     * 并通过 {@code amountLimit} 控制扣费后的最低允许余额。</p>
     *
     * <p>典型使用方式：</p>
     * <p>1. 不允许欠费时，传入 {@code amountLimit = 0}；</p>
     * <p>2. 允许欠费 1000 时，传入 {@code amountLimit = -1000}；</p>
     * <p>3. 不同客户等级对应不同额度时，应由 Service 层先计算出本次生效的
     * {@code amountLimit}，再传入本方法执行原子扣费。</p>
     *
     * <p>SQL 层会把 {@code extend4} 的 {@code null} 或空串按 {@code 0} 处理，
     * 因此可以兼容历史脏数据或未初始化余额的客户记录。</p>
     *
     * @param clientId 客户 id，对应 {@code client_business.id}
     * @param fee 本次扣费金额，应为正数
     * @param amountLimit 扣费后允许达到的最低余额；可为负数，用于表达允许欠费的额度
     * @param updateId 本次操作人 id；允许为 {@code null}，为 {@code null} 时不更新 {@code update_id}
     * @return 受影响行数；返回 {@code 1} 表示扣费成功，返回 {@code 0} 通常表示客户不存在、
     * 已删除，或扣费后余额会低于 {@code amountLimit}
     */
    int debitBalanceAtomic(@Param("clientId") Long clientId,
                           @Param("fee") Long fee,
                           @Param("amountLimit") Long amountLimit,
                           @Param("updateId") Long updateId);

    /**
     * 以原子方式执行余额充值。
     *
     * <p>该方法直接在数据库侧完成“余额加上充值金额”的操作，
     * 不依赖调用方先查询旧余额再拼接新余额，因此可避免并发充值时的丢更新问题。</p>
     *
     * <p>SQL 层会把 {@code extend4} 的 {@code null} 或空串按 {@code 0} 处理，
     * 适用于尚未初始化余额的客户记录。</p>
     *
     * @param clientId 客户 id，对应 {@code client_business.id}
     * @param amount 本次充值金额，应为正数
     * @param updateId 本次操作人 id；允许为 {@code null}，为 {@code null} 时不更新 {@code update_id}
     * @return 受影响行数；返回 {@code 1} 表示充值成功，返回 {@code 0} 通常表示客户不存在或已删除
     */
    int rechargeBalanceAtomic(@Param("clientId") Long clientId,
                              @Param("amount") Long amount,
                              @Param("updateId") Long updateId);

    /**
     * 以原子方式执行余额调账。
     *
     * <p>该方法用于统一处理“按增量调账”的场景：</p>
     * <p>1. {@code delta > 0} 表示加款；</p>
     * <p>2. {@code delta < 0} 表示减款；</p>
     * <p>3. {@code delta = 0} 一般无业务意义，通常应在 Service 层提前拦截。</p>
     *
     * <p>当传入 {@code amountLimit} 时，SQL 会校验调账后的余额不能低于该下限；
     * 当 {@code amountLimit} 为 {@code null} 时，不做最低余额约束。</p>
     *
     * <p>该方法适合人工调账、补偿修正等需要“按差额调整余额”的场景。
     * 如果未来业务语义是“直接改成目标余额”，则更适合单独设计新的 DAO 方法，
     * 而不是复用本方法。</p>
     *
     * @param clientId 客户 id，对应 {@code client_business.id}
     * @param delta 调账增量；正数表示加款，负数表示减款
     * @param amountLimit 调账后允许达到的最低余额；为 {@code null} 时不做下限校验
     * @param updateId 本次操作人 id；允许为 {@code null}，为 {@code null} 时不更新 {@code update_id}
     * @return 受影响行数；返回 {@code 1} 表示调账成功，返回 {@code 0} 通常表示客户不存在、
     * 已删除，或调账后余额会低于 {@code amountLimit}
     */
    int adjustBalanceAtomic(@Param("clientId") Long clientId,
                            @Param("delta") Long delta,
                            @Param("amountLimit") Long amountLimit,
                            @Param("updateId") Long updateId);
}
