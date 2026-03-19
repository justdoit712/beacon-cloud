package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.dto.BalanceCommandResult;
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
    public BalanceCommandResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId) {
        validatePositiveClientId(clientId);
        validatePositiveAmount(fee, "fee");

        long effectiveLimit = amountLimit == null ? DEFAULT_AMOUNT_LIMIT : amountLimit;
        int affected = clientBalanceMapper.debitBalanceAtomic(clientId, fee, effectiveLimit, null);
        if (affected <= 0) {
            return resolveLowerBoundFailure(clientId, effectiveLimit);
        }

        ClientBalance latestBalance = requireLatestClientBalance(clientId);
        ClientBusiness latestBusiness = requireLatestClientBusiness(clientId);
        scheduleBalanceDoubleRefresh(latestBalance, latestBusiness, "debit", safeEntityId(clientId, requestId));
        return BalanceCommandResult.success(latestBalance.getBalance(), effectiveLimit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult rechargeAndSync(Long clientId, Long amount, Long updateId, String requestId) {
        validatePositiveClientId(clientId);
        validatePositiveAmount(amount, "amount");

        int affected = clientBalanceMapper.rechargeBalanceAtomic(clientId, amount, updateId);
        if (affected <= 0) {
            return resolveMissingClientFailure(clientId);
        }

        ClientBalance latestBalance = requireLatestClientBalance(clientId);
        ClientBusiness latestBusiness = requireLatestClientBusiness(clientId);
        scheduleBalanceDoubleRefresh(latestBalance, latestBusiness, "recharge", safeEntityId(clientId, requestId));
        return BalanceCommandResult.success(latestBalance.getBalance(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceCommandResult adjustAndSync(Long clientId, Long delta, Long amountLimit, Long updateId, String requestId) {
        validatePositiveClientId(clientId);
        validateNonZeroDelta(delta);

        int affected = clientBalanceMapper.adjustBalanceAtomic(clientId, delta, amountLimit, updateId);
        if (affected <= 0) {
            if (amountLimit == null) {
                return resolveMissingClientFailure(clientId);
            }
            return resolveLowerBoundFailure(clientId, amountLimit);
        }

        ClientBalance latestBalance = requireLatestClientBalance(clientId);
        ClientBusiness latestBusiness = requireLatestClientBusiness(clientId);
        scheduleBalanceDoubleRefresh(latestBalance, latestBusiness, "adjust", safeEntityId(clientId, requestId));
        return BalanceCommandResult.success(latestBalance.getBalance(), amountLimit);
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
