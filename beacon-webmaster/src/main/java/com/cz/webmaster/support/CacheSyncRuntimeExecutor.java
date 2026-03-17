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
 * 运行时缓存同步执行器。
 *
 * <p>用于根据当前事务状态决定缓存同步动作的执行时机：
 * 有事务时在事务提交后执行，无事务时立即执行。</p>
 */
@Component
public class CacheSyncRuntimeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CacheSyncRuntimeExecutor.class);

    /**
     * 按当前事务状态执行缓存同步动作。
     *
     * <p>执行规则：</p>
     * <p>1. 若当前存在事务，则将动作注册到 {@code afterCommit}；</p>
     * <p>2. 若当前不存在事务，则立即执行；</p>
     * <p>3. 执行过程统一记录同步日志。</p>
     *
     * @param action 需要执行的缓存同步动作
     * @param domain 当前动作所属的缓存域
     * @param operation 当前动作名称，例如 {@code syncUpsert}、{@code syncDelete}
     * @param entityId 当前动作关联的实体标识
     * @throws IllegalArgumentException 当 {@code action} 为 {@code null} 时抛出
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

    /**
     * 判断当前是否存在可挂接 {@code afterCommit} 的事务环境。
     *
     * @return true 表示当前存在事务，false 表示当前不存在事务
     */
    private boolean isTransactionActive() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    /**
     * 执行缓存同步动作，并统一记录成功、失败和降级日志。
     *
     * @param action 缓存同步动作
     * @param domain 缓存域
     * @param operation 操作名称
     * @param entityId 实体标识
     */
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

    /**
     * 根据操作名称推导错误码。
     *
     * @param operation 操作名称
     * @return 对应的错误枚举
     */
    private ExceptionEnums resolveErrorCode(String operation) {
        if (StringUtils.hasText(operation) && operation.toLowerCase().contains("delete")) {
            return ExceptionEnums.CACHE_SYNC_DELETE_FAIL;
        }
        return ExceptionEnums.CACHE_SYNC_WRITE_FAIL;
    }

    /**
     * 判断当前缓存域是否为余额域。
     *
     * @param domain 域编码
     * @return true 表示余额域，false 表示非余额域
     */
    private boolean isBalanceDomain(String domain) {
        return CacheDomainRegistry.CLIENT_BALANCE.equals(domain);
    }

    /**
     * 计算执行耗时。
     *
     * @param startAt 开始时间戳
     * @return 非负耗时毫秒值
     */
    private long costMs(long startAt) {
        return Math.max(System.currentTimeMillis() - startAt, 0);
    }

    /**
     * 将字符串规范化为可安全输出的日志文本。
     *
     * @param value 原始字符串
     * @return 去除首尾空白后的文本；若为空则返回 {@code "-"}
     */
    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
