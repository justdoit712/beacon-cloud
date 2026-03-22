package com.cz.webmaster.rebuild;

import com.cz.webmaster.client.BeaconCacheWriteClient;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CacheRebuildCoordinationSupportTest {

    @Test
    public void shouldAcquireAndReleaseRebuildLockByDomain() {
        BeaconCacheWriteClient client = Mockito.mock(BeaconCacheWriteClient.class);
        CacheRebuildCoordinationSupport support = new CacheRebuildCoordinationSupport(client);

        when(client.setIfAbsent(eq("cache:rebuild:channel"), eq("token-1"), eq(300L))).thenReturn(true);
        when(client.deleteIfValueMatches(eq("cache:rebuild:channel"), eq("token-1"))).thenReturn(true);

        Assert.assertTrue(support.tryAcquireRebuildLock("channel", "token-1"));
        Assert.assertTrue(support.releaseRebuildLock("channel", "token-1"));
    }

    @Test
    public void shouldDetectRunningRebuildAndConsumeDirtyMark() {
        BeaconCacheWriteClient client = Mockito.mock(BeaconCacheWriteClient.class);
        CacheRebuildCoordinationSupport support = new CacheRebuildCoordinationSupport(client);

        when(client.get(eq("cache:rebuild:client_business"))).thenReturn("lock-token");
        when(client.pop(eq("cache:rebuild:dirty:client_business"))).thenReturn("dirty-marker");

        Assert.assertTrue(support.isRebuildRunning("client_business"));
        support.markDirty("client_business", "dirty-marker");
        Assert.assertTrue(support.consumeDirty("client_business"));

        verify(client, times(1)).set(eq("cache:rebuild:dirty:client_business"), eq("dirty-marker"));
    }
}
