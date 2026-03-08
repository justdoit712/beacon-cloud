package com.cz.webmaster.support;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CacheSyncRuntimeExecutor 单元测试。
 * <p>
 * 验证第二层第一步核心行为：
 * 1) 无事务时立即执行；
 * 2) 有事务时提交后执行；
 * 3) 回滚时不执行。
 */
public class CacheSyncRuntimeExecutorTest {

    private final CacheSyncRuntimeExecutor executor = new CacheSyncRuntimeExecutor();

    @After
    public void cleanUpTransactionContext() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    public void shouldRunImmediatelyWhenNoTransaction() {
        AtomicInteger counter = new AtomicInteger(0);

        executor.runAfterCommitOrNow(counter::incrementAndGet, "client_business", "syncUpsert", "1001");

        Assert.assertEquals(1, counter.get());
    }

    @Test
    public void shouldRunAfterCommitWhenTransactionActive() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        AtomicInteger counter = new AtomicInteger(0);
        executor.runAfterCommitOrNow(counter::incrementAndGet, "client_business", "syncUpsert", "1001");

        // 注册后尚未提交，不应执行
        Assert.assertEquals(0, counter.get());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        Assert.assertEquals(1, synchronizations.size());

        // 模拟事务提交
        synchronizations.get(0).afterCommit();
        Assert.assertEquals(1, counter.get());
    }

    @Test
    public void shouldNotRunWhenRollbackOnlyCompletion() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        AtomicInteger counter = new AtomicInteger(0);
        executor.runAfterCommitOrNow(counter::incrementAndGet, "client_business", "syncDelete", "1001");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        Assert.assertEquals(1, synchronizations.size());

        // 模拟事务回滚完成：不会触发 afterCommit
        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        Assert.assertEquals(0, counter.get());
    }
}

