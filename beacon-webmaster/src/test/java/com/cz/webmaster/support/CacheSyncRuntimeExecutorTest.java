package com.cz.webmaster.support;

import com.cz.webmaster.rebuild.CacheRebuildCoordinationSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

        Assert.assertEquals(0, counter.get());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        Assert.assertEquals(1, synchronizations.size());

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

        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        Assert.assertEquals(0, counter.get());
    }

    @Test
    public void shouldNotThrowWhenSyncFailsForNonBalanceDomain() {
        executor.runAfterCommitOrNow(
                () -> {
                    throw new RuntimeException("sync fail");
                },
                "client_business",
                "upsert",
                "1001"
        );
    }

    @Test
    public void shouldNotThrowWhenSyncFailsForBalanceDomain() {
        executor.runAfterCommitOrNow(
                () -> {
                    throw new RuntimeException("sync fail");
                },
                "client_balance",
                "upsert",
                "1001"
        );
    }

    @Test
    public void shouldMarkDirtyAndSkipWhenRebuildRunning() {
        CacheRebuildCoordinationSupport support = Mockito.mock(CacheRebuildCoordinationSupport.class);
        CacheSyncRuntimeExecutor guardedExecutor = new CacheSyncRuntimeExecutor(support);
        Mockito.when(support.isRebuildRunning("client_business")).thenReturn(true);

        AtomicInteger counter = new AtomicInteger(0);
        guardedExecutor.runAfterCommitOrNow(counter::incrementAndGet, "client_business", "syncUpsert", "1001");

        Assert.assertEquals(0, counter.get());
        Mockito.verify(support, Mockito.times(1))
                .markDirty(Mockito.eq("client_business"), Mockito.contains("syncUpsert.immediate|1001|"));
    }
}
