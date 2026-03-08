package com.cz.webmaster.support;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.common.enums.ExceptionEnums;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Runtime sync dispatcher.
 *
 * Rules:
 * 1) with transaction: execute after commit;
 * 2) without transaction: execute immediately;
 * 3) sync failure is observable but does not block main flow.
 */
@Component
public class CacheSyncRuntimeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CacheSyncRuntimeExecutor.class);

    /**
     * Execute cache sync action after commit when transaction exists, otherwise execute now.
     */
    public void runAfterCommitOrNow(Runnable action, String domain, String operation, String entityId) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        String safeDomain = safe(domain);
        String safeOperation = safe(operation);
        String safeEntityId = safe(entityId);

        if (isTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAction(action, safeDomain, safeOperation + ".afterCommit", safeEntityId);
                }
            });
            CacheSyncLogHelper.info(log, safeDomain, safeEntityId, "-", safeOperation + ".scheduledAfterCommit", 0L);
            return;
        }

        runAction(action, safeDomain, safeOperation + ".immediate", safeEntityId);
    }

    private boolean isTransactionActive() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    private void runAction(Runnable action, String domain, String operation, String entityId) {
        long startAt = System.currentTimeMillis();
        try {
            action.run();
            CacheSyncLogHelper.info(log, domain, entityId, "-", operation, costMs(startAt));
        } catch (Exception ex) {
            long cost = costMs(startAt);
            ExceptionEnums errorCode = resolveErrorCode(operation);
            CacheSyncLogHelper.error(
                    log,
                    domain,
                    entityId,
                    "-",
                    operation,
                    cost,
                    errorCode,
                    ex.getMessage(),
                    ex
            );

            if (isBalanceDomain(domain)) {
                CacheSyncLogHelper.warn(
                        log,
                        domain,
                        entityId,
                        "-",
                        operation + ".compensationPlaceholder",
                        cost,
                        errorCode,
                        "mysql committed; cache sync failed; compensation queue placeholder logged",
                        null
                );
                return;
            }

            CacheSyncLogHelper.warn(
                    log,
                    domain,
                    entityId,
                    "-",
                    operation + ".degradeContinue",
                    cost,
                    errorCode,
                    "runtime sync failed but main flow continues",
                    null
            );
        }
    }

    private ExceptionEnums resolveErrorCode(String operation) {
        if (StringUtils.hasText(operation) && operation.toLowerCase().contains("delete")) {
            return ExceptionEnums.CACHE_SYNC_DELETE_FAIL;
        }
        return ExceptionEnums.CACHE_SYNC_WRITE_FAIL;
    }

    private boolean isBalanceDomain(String domain) {
        return CacheDomainRegistry.CLIENT_BALANCE.equals(domain);
    }

    private long costMs(long startAt) {
        return Math.max(System.currentTimeMillis() - startAt, 0);
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
