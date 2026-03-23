package com.cz.webmaster.service.impl;

import com.cz.webmaster.dto.CacheRebuildReport;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CacheRebuildServiceImplTest {

    @Test
    public void shouldDelegateRebuildToCacheSyncServiceImpl() {
        CacheSyncServiceImpl cacheSyncService = Mockito.mock(CacheSyncServiceImpl.class);
        CacheRebuildServiceImpl cacheRebuildService = new CacheRebuildServiceImpl(cacheSyncService);

        CacheRebuildReport report = new CacheRebuildReport();
        report.setDomain("channel");
        report.setStatus("SUCCESS");
        when(cacheSyncService.rebuildDomain("channel")).thenReturn(report);

        CacheRebuildReport result = cacheRebuildService.rebuildDomain("channel");

        Assert.assertSame(report, result);
        verify(cacheSyncService, times(1)).rebuildDomain("channel");
    }

    @Test
    public void shouldDelegateBootRebuildToCacheSyncServiceImpl() {
        CacheSyncServiceImpl cacheSyncService = Mockito.mock(CacheSyncServiceImpl.class);
        CacheRebuildServiceImpl cacheRebuildService = new CacheRebuildServiceImpl(cacheSyncService);

        CacheRebuildReport report = new CacheRebuildReport();
        report.setDomain("channel");
        report.setTrigger("BOOT");
        report.setStatus("SUCCESS");
        when(cacheSyncService.rebuildBootDomain("channel")).thenReturn(report);

        CacheRebuildReport result = cacheRebuildService.rebuildBootDomain("channel");

        Assert.assertSame(report, result);
        verify(cacheSyncService, times(1)).rebuildBootDomain("channel");
    }
}
