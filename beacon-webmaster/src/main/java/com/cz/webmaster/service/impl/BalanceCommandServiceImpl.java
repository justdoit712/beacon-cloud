package com.cz.webmaster.service.impl;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceAdjustCommand;
import com.cz.webmaster.dto.ClientBalanceDebitCommand;
import com.cz.webmaster.dto.ClientBalanceRechargeCommand;
import com.cz.webmaster.entity.ClientBalance;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.enums.BalanceCommandStatus;
import com.cz.webmaster.mapper.ClientBalanceMapper;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.BalanceCommandService;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 余额命令服务实现。
 *
 * <p>该实现以 {@code client_balance} 为余额真源，统一处理扣费、充值、调账三类命令，
 * 并在事务提交后同步刷新余额和商户业务缓存。</p>
 *
 * <p>处理原则如下：</p>
 * <ol>
 *     <li>先执行数据库原子更新，再回查最新快照作为返回与缓存同步依据。</li>
 *     <li>缓存刷新通过 {@link CacheSyncRuntimeExecutor} 在提交后执行，避免脏读或回滚污染。</li>
 *     <li>命令入参统一做校验与归一化，确保链路输入稳定。</li>
 * </ol>
 */
@Service
public class BalanceCommandServiceImpl implements BalanceCommandService {

    /**
     * 默认余额下限，扣费命令未显式传入下限时使用。
     */
    private static final long DEFAULT_AMOUNT_LIMIT = -10000L;

    private final ClientBalanceMapper clientBalanceMapper;
    private final ClientBusinessMapper clientBusinessMapper;
    private final CacheSyncService cacheSyncService;
    private final CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    public BalanceCommandServiceImpl(ClientBalanceMapper clientBalanceMapper,
                                     ClientBusinessMapper clientBusinessMapper,
                                     CacheSyncService cacheSyncService,
                                     CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor) {
        this.clientBalanceMapper = clientBalanceMapper;
        this.clientBusinessMapper = clientBusinessMapper;
        this.cacheSyncService = cacheSyncService;
        this.cacheSyncRuntimeExecutor = cacheSyncRuntimeExecutor;
    }

    /**
     * 执行扣费并同步缓存。
     *
     * <p>流程：校验并归一化命令 -> 原子扣减余额 -> 回查最新余额/业务数据 -> 提交后刷新缓存。</p>
     *
     * @param command 扣费命令
     * @return 命令执行结果；失败时返回余额不足或客户不存在状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult debitAndSync(ClientBalanceDebitCommand command) {
        ClientBalanceDebitCommand normalized = normalizeDebitCommand(command);
        int affected = clientBalanceMapper.debitBalanceAtomic(
                normalized.getClientId(),
                normalized.getFee(),
                normalized.getAmountLimit(),
                normalized.getOperatorId()
        );
        if (affected <= 0) {
            return resolveLowerBoundFailure(normalized.getClientId(), normalized.getAmountLimit());
        }

        ClientBalance latestBalance = requireLatestClientBalance(normalized.getClientId());
        ClientBusiness latestBusiness = requireLatestClientBusiness(normalized.getClientId());
        scheduleBalanceDoubleRefresh(latestBalance, latestBusiness, "debit", safeEntityId(normalized.getClientId(), normalized.getRequestId()));
        return BalanceCommandResult.success(latestBalance.getBalance(), normalized.getAmountLimit());
    }

    /**
     * 执行充值并同步缓存。
     *
     * @param command 充值命令
     * @return 命令执行结果；失败时返回客户不存在状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult rechargeAndSync(ClientBalanceRechargeCommand command) {
        ClientBalanceRechargeCommand normalized = normalizeRechargeCommand(command);
        int affected = clientBalanceMapper.rechargeBalanceAtomic(
                normalized.getClientId(),
                normalized.getAmount(),
                normalized.getOperatorId()
        );
        if (affected <= 0) {
            return resolveMissingClientFailure(normalized.getClientId());
        }

        ClientBalance latestBalance = requireLatestClientBalance(normalized.getClientId());
        ClientBusiness latestBusiness = requireLatestClientBusiness(normalized.getClientId());
        scheduleBalanceDoubleRefresh(latestBalance, latestBusiness, "recharge", safeEntityId(normalized.getClientId(), normalized.getRequestId()));
        return BalanceCommandResult.success(latestBalance.getBalance(), null);
    }

    /**
     * 执行调账并同步缓存。
     *
     * <p>当命令包含 {@code amountLimit} 时，更新失败会按下限校验失败处理；
     * 未包含下限时，按客户不存在分支处理。</p>
     *
     * @param command 调账命令
     * @return 命令执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult adjustAndSync(ClientBalanceAdjustCommand command) {
        ClientBalanceAdjustCommand normalized = normalizeAdjustCommand(command);
        int affected = clientBalanceMapper.adjustBalanceAtomic(
                normalized.getClientId(),
                normalized.getDelta(),
                normalized.getAmountLimit(),
                normalized.getOperatorId()
        );
        if (affected <= 0) {
            if (normalized.getAmountLimit() == null) {
                return resolveMissingClientFailure(normalized.getClientId());
            }
            return resolveLowerBoundFailure(normalized.getClientId(), normalized.getAmountLimit());
        }

        ClientBalance latestBalance = requireLatestClientBalance(normalized.getClientId());
        ClientBusiness latestBusiness = requireLatestClientBusiness(normalized.getClientId());
        scheduleBalanceDoubleRefresh(latestBalance, latestBusiness, "adjust", safeEntityId(normalized.getClientId(), normalized.getRequestId()));
        return BalanceCommandResult.success(latestBalance.getBalance(), normalized.getAmountLimit());
    }

    /**
     * 归一化扣费命令，补齐默认余额下限并清理可选字符串字段。
     *
     * @param command 原始扣费命令
     * @return 归一化后的命令对象
     * @throws IllegalArgumentException 当命令为空或关键字段非法时抛出
     */
    private ClientBalanceDebitCommand normalizeDebitCommand(ClientBalanceDebitCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("debit command must not be null");
        }
        validatePositiveClientId(command.getClientId());
        validatePositiveAmount(command.getFee(), "fee");

        ClientBalanceDebitCommand normalized = new ClientBalanceDebitCommand();
        normalized.setClientId(command.getClientId());
        normalized.setFee(command.getFee());
        normalized.setAmountLimit(command.getAmountLimit() == null ? DEFAULT_AMOUNT_LIMIT : command.getAmountLimit());
        normalized.setOperatorId(command.getOperatorId());
        normalized.setRequestId(trimToNull(command.getRequestId()));
        return normalized;
    }

    /**
     * 归一化充值命令并执行基础参数校验。
     *
     * @param command 原始充值命令
     * @return 归一化后的命令对象
     * @throws IllegalArgumentException 当命令为空或关键字段非法时抛出
     */
    private ClientBalanceRechargeCommand normalizeRechargeCommand(ClientBalanceRechargeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("recharge command must not be null");
        }
        validatePositiveClientId(command.getClientId());
        validatePositiveAmount(command.getAmount(), "amount");

        ClientBalanceRechargeCommand normalized = new ClientBalanceRechargeCommand();
        normalized.setClientId(command.getClientId());
        normalized.setAmount(command.getAmount());
        normalized.setOperatorId(command.getOperatorId());
        normalized.setRequestId(trimToNull(command.getRequestId()));
        return normalized;
    }

    /**
     * 归一化调账命令并执行基础参数校验。
     *
     * @param command 原始调账命令
     * @return 归一化后的命令对象
     * @throws IllegalArgumentException 当命令为空或关键字段非法时抛出
     */
    private ClientBalanceAdjustCommand normalizeAdjustCommand(ClientBalanceAdjustCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("adjust command must not be null");
        }
        validatePositiveClientId(command.getClientId());
        validateNonZeroDelta(command.getDelta());

        ClientBalanceAdjustCommand normalized = new ClientBalanceAdjustCommand();
        normalized.setClientId(command.getClientId());
        normalized.setDelta(command.getDelta());
        normalized.setAmountLimit(command.getAmountLimit());
        normalized.setOperatorId(command.getOperatorId());
        normalized.setRequestId(trimToNull(command.getRequestId()));
        return normalized;
    }

    /**
     * 查询最新余额记录。
     *
     * @param clientId 客户 ID
     * @return 最新余额记录；当 ID 非法时返回 {@code null}
     */
    private ClientBalance loadLatestClientBalance(Long clientId) {
        if (clientId == null || clientId <= 0) {
            return null;
        }
        return clientBalanceMapper.selectByClientId(clientId);
    }

    /**
     * 查询最新商户业务记录。
     *
     * @param clientId 客户 ID
     * @return 最新商户业务记录；当 ID 非法时返回 {@code null}
     */
    private ClientBusiness loadLatestClientBusiness(Long clientId) {
        if (clientId == null || clientId <= 0) {
            return null;
        }
        return clientBusinessMapper.selectByPrimaryKey(clientId);
    }

    /**
     * 在事务提交后触发余额和业务缓存双刷新。
     *
     * <p>为兼容缓存结构，余额数据先转换为键值对负载再执行 upsert；商户业务数据按实体直接 upsert。</p>
     *
     * @param latestBalance 最新余额快照
     * @param latestBusiness 最新商户业务快照
     * @param operation 操作名（用于日志/链路标识）
     * @param entityId 实体标识（用于日志/链路标识）
     */
    private void scheduleBalanceDoubleRefresh(ClientBalance latestBalance,
                                              ClientBusiness latestBusiness,
                                              String operation,
                                              String entityId) {
        ClientBalance refreshableBalance = requireRefreshableClientBalance(latestBalance);
        ClientBusiness refreshableBusiness = requireRefreshableClientBusiness(latestBusiness);
        Map<String, Object> clientBalancePayload = buildClientBalancePayload(refreshableBalance);

        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_BALANCE, clientBalancePayload),
                CacheDomainRegistry.CLIENT_BALANCE,
                operation + ".clientBalance",
                entityId
        );
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, refreshableBusiness),
                CacheDomainRegistry.CLIENT_BUSINESS,
                operation + ".clientBusiness",
                entityId
        );
    }

    /**
     * 解析扣费/带下限调账失败原因。
     *
     * @param clientId 客户 ID
     * @param amountLimit 本次命令使用的余额下限
     * @return 若客户不存在则返回客户不存在，否则返回余额不足
     */
    private BalanceCommandResult resolveLowerBoundFailure(Long clientId, Long amountLimit) {
        ClientBalance latestBalance = loadLatestClientBalance(clientId);
        if (isMissingOrDeletedBalance(latestBalance)) {
            return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, amountLimit);
        }
        return BalanceCommandResult.failure(BalanceCommandStatus.BALANCE_NOT_ENOUGH, amountLimit);
    }

    /**
     * 解析充值或不带下限调账失败原因。
     *
     * @param clientId 客户 ID
     * @return 失败结果（当前统一为客户不存在）
     */
    private BalanceCommandResult resolveMissingClientFailure(Long clientId) {
        ClientBalance latestBalance = loadLatestClientBalance(clientId);
        if (isMissingOrDeletedBalance(latestBalance)) {
            return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, null);
        }
        return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, null);
    }

    /**
     * 获取并校验最新余额快照。
     *
     * @param clientId 客户 ID
     * @return 有效的余额记录
     * @throws IllegalStateException 当数据缺失或已删除时抛出
     */
    private ClientBalance requireLatestClientBalance(Long clientId) {
        ClientBalance latest = loadLatestClientBalance(clientId);
        if (isMissingOrDeletedBalance(latest)) {
            throw new IllegalStateException("latest client balance not found after balance command");
        }
        return latest;
    }

    /**
     * 获取并校验最新商户业务快照。
     *
     * @param clientId 客户 ID
     * @return 有效的商户业务记录
     * @throws IllegalStateException 当数据缺失或已删除时抛出
     */
    private ClientBusiness requireLatestClientBusiness(Long clientId) {
        ClientBusiness latest = loadLatestClientBusiness(clientId);
        if (isMissingOrDeletedBusiness(latest)) {
            throw new IllegalStateException("latest client business not found after balance command");
        }
        return latest;
    }

    /**
     * 将余额实体转换为缓存同步所需的扁平负载。
     *
     * @param latest 最新余额记录
     * @return 缓存 upsert 负载
     */
    private Map<String, Object> buildClientBalancePayload(ClientBalance latest) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", latest.getId());
        payload.put("clientId", latest.getClientId());
        payload.put("balance", latest.getBalance());
        payload.put("created", latest.getCreated());
        payload.put("createId", latest.getCreateId());
        payload.put("updated", latest.getUpdated());
        payload.put("updateId", latest.getUpdateId());
        payload.put("isDelete", latest.getIsDelete());
        payload.put("extend1", latest.getExtend1());
        payload.put("extend2", latest.getExtend2());
        payload.put("extend3", latest.getExtend3());
        return payload;
    }

    /**
     * 校验余额实体是否满足缓存刷新前置条件。
     *
     * @param latest 最新余额记录
     * @return 可用于刷新缓存的余额记录
     * @throws IllegalStateException 当关键字段缺失或非法时抛出
     */
    private ClientBalance requireRefreshableClientBalance(ClientBalance latest) {
        if (latest == null) {
            throw new IllegalStateException("latest client balance must not be null");
        }
        if (latest.getClientId() == null || latest.getClientId() <= 0) {
            throw new IllegalStateException("latest client balance clientId must be positive");
        }
        if (latest.getBalance() == null) {
            throw new IllegalStateException("latest client balance must not be null");
        }
        return latest;
    }

    /**
     * 校验商户业务实体是否满足缓存刷新前置条件。
     *
     * @param latest 最新商户业务记录
     * @return 可用于刷新缓存的商户业务记录
     * @throws IllegalStateException 当关键字段缺失或非法时抛出
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

    /**
     * 判断余额记录是否不存在或已逻辑删除。
     *
     * @param latest 余额记录
     * @return {@code true} 表示不可用
     */
    private boolean isMissingOrDeletedBalance(ClientBalance latest) {
        return latest == null
                || latest.getIsDelete() == null
                ? latest == null
                : latest.getIsDelete().byteValue() != 0;
    }

    /**
     * 判断商户业务记录是否不存在或已逻辑删除。
     *
     * @param latest 商户业务记录
     * @return {@code true} 表示不可用
     */
    private boolean isMissingOrDeletedBusiness(ClientBusiness latest) {
        return latest == null
                || latest.getIsDelete() == null
                ? latest == null
                : latest.getIsDelete().byteValue() != 0;
    }

    /**
     * 生成缓存同步链路的实体标识。
     *
     * @param clientId 客户 ID
     * @param requestId 请求 ID
     * @return 可读且稳定的实体标识
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

    /**
     * 去除字符串首尾空白；空白字符串按 {@code null} 处理。
     *
     * @param value 原始字符串
     * @return 处理后的字符串
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 校验客户 ID 必须为正数。
     *
     * @param clientId 客户 ID
     * @throws IllegalArgumentException 当值为空或非正数时抛出
     */
    private void validatePositiveClientId(Long clientId) {
        if (clientId == null || clientId <= 0) {
            throw new IllegalArgumentException("clientId must be positive");
        }
    }

    /**
     * 校验金额字段必须为正数。
     *
     * @param amount 金额值
     * @param fieldName 字段名（用于异常提示）
     * @throws IllegalArgumentException 当值为空或非正数时抛出
     */
    private void validatePositiveAmount(Long amount, String fieldName) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    /**
     * 校验调账增量不能为 0。
     *
     * @param delta 调账增量
     * @throws IllegalArgumentException 当值为空或为 0 时抛出
     */
    private void validateNonZeroDelta(Long delta) {
        if (delta == null || delta == 0L) {
            throw new IllegalArgumentException("delta must not be zero");
        }
    }
}
