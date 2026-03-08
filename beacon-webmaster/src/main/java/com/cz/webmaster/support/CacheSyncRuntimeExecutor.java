package com.cz.webmaster.support;

import com.cz.common.enums.ExceptionEnums;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Runtime Sync 触发执行器。
 * <p>
 * 用于统一处理“事务提交后执行”与“无事务立即执行”两种场景：
 * <p>
 * 1. 当前存在事务：注册 afterCommit 回调，提交成功后再执行同步动作；<br>
 * 2. 当前无事务：立即执行同步动作（兼容非事务写路径）。
 */
@Component
public class CacheSyncRuntimeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CacheSyncRuntimeExecutor.class);

    /**
     * 根据当前事务上下文决定执行时机。
     *
     * @param action    具体同步动作
     * @param domain    缓存域编码（用于日志）
     * @param operation 操作名（用于日志）
     * @param entityId  实体标识（用于日志）
     */
    public void runAfterCommitOrNow(Runnable action, String domain, String operation, String entityId) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        String safeDomain = safe(domain);
        String safeOperation = safe(operation);
        String safeEntityId = safe(entityId);

        // 有事务且可注册同步回调：提交后再执行，避免 DB 回滚但缓存已更新。
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

        // 无事务：直接执行，兼容当前仍未统一事务化的写路径。
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
            CacheSyncLogHelper.error(
                    log,
                    domain,
                    entityId,
                    "-",
                    operation,
                    costMs(startAt),
                    resolveErrorCode(operation),
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    private ExceptionEnums resolveErrorCode(String operation) {
        if (StringUtils.hasText(operation) && operation.toLowerCase().contains("delete")) {
            return ExceptionEnums.CACHE_SYNC_DELETE_FAIL;
        }
        return ExceptionEnums.CACHE_SYNC_WRITE_FAIL;
    }

    private long costMs(long startAt) {
        return Math.max(System.currentTimeMillis() - startAt, 0);
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}

