package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.enums.BalanceCommandStatus;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.BalanceCommandService;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * {@link BalanceCommandService} 的默认实现。
 *
 * <p>当前类先提供余额链路的公共能力骨架，不直接展开扣费、充值、调账
 * 三个命令的具体业务细节。后续各命令实现时，应统一复用这里沉淀的公共流程。</p>
 *
 * <p>当前已沉淀的公共能力包括：</p>
 * <p>1. 查询最新的 {@link ClientBusiness} 真源记录；</p>
 * <p>2. 构造用于日志串联的实体标识；</p>
 * <p>3. 在事务提交成功后，同时刷新 {@code client_balance} 和
 * {@code client_business} 两个缓存域。</p>
 */
@Service
public class BalanceCommandServiceImpl implements BalanceCommandService {

    private static final long DEFAULT_AMOUNT_LIMIT = -10000L;

    private final ClientBusinessMapper clientBusinessMapper;
    private final CacheSyncService cacheSyncService;
    private final CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    /**
     * 构造统一余额命令服务实现。
     *
     * @param clientBusinessMapper 客户业务 Mapper
     * @param cacheSyncService 缓存同步统一门面
     * @param cacheSyncRuntimeExecutor 运行时缓存同步执行器
     */
    public BalanceCommandServiceImpl(ClientBusinessMapper clientBusinessMapper,
                                     CacheSyncService cacheSyncService,
                                     CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor) {
        this.clientBusinessMapper = clientBusinessMapper;
        this.cacheSyncService = cacheSyncService;
        this.cacheSyncRuntimeExecutor = cacheSyncRuntimeExecutor;
    }

    /**
     * 扣费命令入口。
     *
     * <p>当前阶段只先定义统一入口，具体扣费细节在后续任务中补齐。</p>
     *
     * @param clientId 客户 id
     * @param fee 扣费金额
     * @param amountLimit 最低余额限制
     * @param requestId 请求标识
     * @return 余额命令执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId) {
        validatePositiveClientId(clientId);
        validatePositiveAmount(fee, "fee");

        long effectiveLimit = amountLimit == null ? DEFAULT_AMOUNT_LIMIT : amountLimit;
        int affected = clientBusinessMapper.debitBalanceAtomic(clientId, fee, effectiveLimit, null);
        if (affected <= 0) {
            return resolveLowerBoundFailure(clientId, effectiveLimit);
        }

        ClientBusiness latest = requireLatestClientBusiness(clientId);
        scheduleBalanceDoubleRefresh(latest, "debit", safeEntityId(clientId, requestId));
        return BalanceCommandResult.success(parseBalance(latest.getExtend4()), effectiveLimit);
    }

    /**
     * 充值命令入口。
     *
     * <p>当前阶段只先定义统一入口，具体充值细节在后续任务中补齐。</p>
     *
     * @param clientId 客户 id
     * @param amount 充值金额
     * @param updateId 操作人 id
     * @param requestId 请求标识
     * @return 余额命令执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult rechargeAndSync(Long clientId, Long amount, Long updateId, String requestId) {
        validatePositiveClientId(clientId);
        validatePositiveAmount(amount, "amount");

        int affected = clientBusinessMapper.rechargeBalanceAtomic(clientId, amount, updateId);
        if (affected <= 0) {
            return resolveMissingClientFailure(clientId);
        }

        ClientBusiness latest = requireLatestClientBusiness(clientId);
        scheduleBalanceDoubleRefresh(latest, "recharge", safeEntityId(clientId, requestId));
        return BalanceCommandResult.success(parseBalance(latest.getExtend4()), null);
    }

    /**
     * 调账命令入口。
     *
     * <p>当前阶段只先定义统一入口，具体调账细节在后续任务中补齐。</p>
     *
     * @param clientId 客户 id
     * @param delta 调账增量
     * @param amountLimit 最低余额限制
     * @param updateId 操作人 id
     * @param requestId 请求标识
     * @return 余额命令执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult adjustAndSync(Long clientId, Long delta, Long amountLimit, Long updateId, String requestId) {
        validatePositiveClientId(clientId);
        validateNonZeroDelta(delta);

        int affected = clientBusinessMapper.adjustBalanceAtomic(clientId, delta, amountLimit, updateId);
        if (affected <= 0) {
            if (amountLimit == null) {
                return resolveMissingClientFailure(clientId);
            }
            return resolveLowerBoundFailure(clientId, amountLimit);
        }

        ClientBusiness latest = requireLatestClientBusiness(clientId);
        scheduleBalanceDoubleRefresh(latest, "adjust", safeEntityId(clientId, requestId));
        return BalanceCommandResult.success(parseBalance(latest.getExtend4()), amountLimit);
    }

    /**
     * 查询指定客户最新的真源记录。
     *
     * <p>余额相关命令在原子 SQL 执行成功后，可统一通过该方法读取最新状态，
     * 作为后续结果返回和缓存刷新的基础数据。</p>
     *
     * @param clientId 客户 id
     * @return 最新的客户业务记录；未命中时返回 {@code null}
     */
    private ClientBusiness loadLatestClientBusiness(Long clientId) {
        if (clientId == null || clientId <= 0) {
            return null;
        }
        return clientBusinessMapper.selectByPrimaryKey(clientId);
    }

    /**
     * 在事务提交成功后执行余额双域刷新。
     *
     * <p>当前统一刷新两个缓存域：</p>
     * <p>1. {@code client_balance:{clientId}}</p>
     * <p>2. {@code client_business:{apiKey}}</p>
     *
     * <p>调用前要求 {@code latest} 已经是最新的真源记录，并且包含有效的
     * {@code id} 和 {@code apiKey}。</p>
     *
     * @param latest 最新的客户业务记录
     * @param operation 当前业务操作名称，用于日志区分
     * @param entityId 日志实体标识
     */
    private void scheduleBalanceDoubleRefresh(ClientBusiness latest, String operation, String entityId) {
        ClientBusiness refreshTarget = requireRefreshableClientBusiness(latest);
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_BALANCE, refreshTarget),
                CacheDomainRegistry.CLIENT_BALANCE,
                operation + ".clientBalance",
                entityId
        );
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, refreshTarget),
                CacheDomainRegistry.CLIENT_BUSINESS,
                operation + ".clientBusiness",
                entityId
        );
    }

    private BalanceCommandResult resolveLowerBoundFailure(Long clientId, Long amountLimit) {
        ClientBusiness latest = loadLatestClientBusiness(clientId);
        if (isMissingOrDeleted(latest)) {
            return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, amountLimit);
        }
        return BalanceCommandResult.failure(BalanceCommandStatus.BALANCE_NOT_ENOUGH, amountLimit);
    }

    private BalanceCommandResult resolveMissingClientFailure(Long clientId) {
        ClientBusiness latest = loadLatestClientBusiness(clientId);
        if (isMissingOrDeleted(latest)) {
            return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, null);
        }
        return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, null);
    }

    private ClientBusiness requireLatestClientBusiness(Long clientId) {
        ClientBusiness latest = loadLatestClientBusiness(clientId);
        if (isMissingOrDeleted(latest)) {
            throw new IllegalStateException("latest client business not found after balance command");
        }
        return latest;
    }

    /**
     * 校验最新客户记录是否满足双域刷新的基本条件。
     *
     * @param latest 最新的客户业务记录
     * @return 可用于刷新的客户业务记录
     * @throws IllegalStateException 当记录为空、缺少客户 id 或缺少 apiKey 时抛出
     */
    private ClientBusiness requireRefreshableClientBusiness(ClientBusiness latest) {
        if (latest == null) {
            throw new IllegalStateException("latest client business must not be null");
        }
        if (latest.getId() == null || latest.getId() <= 0) {
            throw new IllegalStateException("latest client business id must be positive");
        }
        if (!StringUtils.hasText(latest.getApikey())) {
            throw new IllegalStateException("latest client business apiKey must not be blank");
        }
        return latest;
    }

    private boolean isMissingOrDeleted(ClientBusiness latest) {
        return latest == null
                || latest.getIsDelete() == null
                ? latest == null
                : latest.getIsDelete().byteValue() != 0;
    }

    /**
     * 构造用于日志串联的实体标识。
     *
     * <p>若同时存在客户 id 和请求 id，则拼成
     * {@code clientId:requestId}，方便在日志里关联同一次业务请求。</p>
     *
     * @param clientId 客户 id
     * @param requestId 请求标识
     * @return 用于日志输出的安全实体标识；无有效客户 id 时返回 {@code "-"}
     */
    private String safeEntityId(Long clientId, String requestId) {
        if (clientId == null || clientId <= 0) {
            return "-";
        }
        if (!StringUtils.hasText(requestId)) {
            return String.valueOf(clientId);
        }
        return clientId + ":" + requestId.trim();
    }

    private long parseBalance(String text) {
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private void validatePositiveClientId(Long clientId) {
        if (clientId == null || clientId <= 0) {
            throw new IllegalArgumentException("clientId must be positive");
        }
    }

    private void validatePositiveAmount(Long amount, String fieldName) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void validateNonZeroDelta(Long delta) {
        if (delta == null || delta == 0L) {
            throw new IllegalArgumentException("delta must not be zero");
        }
    }
}
