package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.ClientBalanceDebitService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClientBalanceDebitServiceImpl implements ClientBalanceDebitService {

    private static final long DEFAULT_AMOUNT_LIMIT = -10000L;

    private final ClientBusinessMapper clientBusinessMapper;
    private final CacheSyncService cacheSyncService;
    private final CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    public ClientBalanceDebitServiceImpl(ClientBusinessMapper clientBusinessMapper,
                                         CacheSyncService cacheSyncService,
                                         CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor) {
        this.clientBusinessMapper = clientBusinessMapper;
        this.cacheSyncService = cacheSyncService;
        this.cacheSyncRuntimeExecutor = cacheSyncRuntimeExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DebitResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId) {
        if (clientId == null || clientId <= 0) {
            throw new IllegalArgumentException("clientId must be positive");
        }
        if (fee == null || fee <= 0) {
            throw new IllegalArgumentException("fee must be positive");
        }

        long effectiveLimit = amountLimit == null ? DEFAULT_AMOUNT_LIMIT : amountLimit;
        int affected = clientBusinessMapper.debitBalanceAtomic(clientId, fee, effectiveLimit, null);
        if (affected <= 0) {
            return new DebitResult(false, null, effectiveLimit, "balance not enough");
        }

        ClientBusiness latest = clientBusinessMapper.selectByPrimaryKey(clientId);
        if (latest == null) {
            return new DebitResult(false, null, effectiveLimit, "client not found");
        }

        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_BALANCE, latest),
                CacheDomainRegistry.CLIENT_BALANCE,
                "upsert",
                safeEntityId(clientId, requestId)
        );
        return new DebitResult(true, parseBalance(latest.getExtend4()), effectiveLimit, "ok");
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

    private String safeEntityId(Long clientId, String requestId) {
        if (clientId == null) {
            return "-";
        }
        if (!StringUtils.hasText(requestId)) {
            return String.valueOf(clientId);
        }
        return clientId + ":" + requestId.trim();
    }
}

