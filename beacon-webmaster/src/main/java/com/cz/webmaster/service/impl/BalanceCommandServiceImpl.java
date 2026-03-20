package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
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
 * Default balance command service backed by {@code client_balance}.
 */
@Service
public class BalanceCommandServiceImpl implements BalanceCommandService {

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

    private ClientBalance loadLatestClientBalance(Long clientId) {
        if (clientId == null || clientId <= 0) {
            return null;
        }
        return clientBalanceMapper.selectByClientId(clientId);
    }

    private ClientBusiness loadLatestClientBusiness(Long clientId) {
        if (clientId == null || clientId <= 0) {
            return null;
        }
        return clientBusinessMapper.selectByPrimaryKey(clientId);
    }

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

    private BalanceCommandResult resolveLowerBoundFailure(Long clientId, Long amountLimit) {
        ClientBalance latestBalance = loadLatestClientBalance(clientId);
        if (isMissingOrDeletedBalance(latestBalance)) {
            return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, amountLimit);
        }
        return BalanceCommandResult.failure(BalanceCommandStatus.BALANCE_NOT_ENOUGH, amountLimit);
    }

    private BalanceCommandResult resolveMissingClientFailure(Long clientId) {
        ClientBalance latestBalance = loadLatestClientBalance(clientId);
        if (isMissingOrDeletedBalance(latestBalance)) {
            return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, null);
        }
        return BalanceCommandResult.failure(BalanceCommandStatus.CLIENT_NOT_FOUND, null);
    }

    private ClientBalance requireLatestClientBalance(Long clientId) {
        ClientBalance latest = loadLatestClientBalance(clientId);
        if (isMissingOrDeletedBalance(latest)) {
            throw new IllegalStateException("latest client balance not found after balance command");
        }
        return latest;
    }

    private ClientBusiness requireLatestClientBusiness(Long clientId) {
        ClientBusiness latest = loadLatestClientBusiness(clientId);
        if (isMissingOrDeletedBusiness(latest)) {
            throw new IllegalStateException("latest client business not found after balance command");
        }
        return latest;
    }

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

    private boolean isMissingOrDeletedBalance(ClientBalance latest) {
        return latest == null
                || latest.getIsDelete() == null
                ? latest == null
                : latest.getIsDelete().byteValue() != 0;
    }

    private boolean isMissingOrDeletedBusiness(ClientBusiness latest) {
        return latest == null
                || latest.getIsDelete() == null
                ? latest == null
                : latest.getIsDelete().byteValue() != 0;
    }

    private String safeEntityId(Long clientId, String requestId) {
        if (clientId == null || clientId <= 0) {
            return "-";
        }
        if (!StringUtils.hasText(requestId)) {
            return String.valueOf(clientId);
        }
        return clientId + ":" + requestId.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
