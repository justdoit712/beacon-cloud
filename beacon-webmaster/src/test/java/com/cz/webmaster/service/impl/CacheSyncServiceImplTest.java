package com.cz.webmaster.service.impl;

import com.cz.common.exception.ApiException;
import com.cz.webmaster.client.BeaconCacheWriteClient;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.support.CacheKeyBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CacheSyncServiceImplTest {

    private BeaconCacheWriteClient cacheWriteClient;
    private CacheSyncServiceImpl cacheSyncService;

    @Before
    public void setUp() {
        cacheWriteClient = Mockito.mock(BeaconCacheWriteClient.class);

        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
        properties.setEnabled(true);
        properties.getRuntime().setEnabled(true);
        properties.getManual().setEnabled(true);
        properties.getRedis().setNamespace("beacon:dev:beacon-cloud:cz:");
        properties.validate();

        cacheSyncService = new CacheSyncServiceImpl(
                properties,
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper()
        );
    }

    @Test
    public void shouldRouteClientBusinessUpsertToHmset() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("apikey", "ak_001");
        payload.put("corpname", "demoCorp");

        cacheSyncService.syncUpsert("client_business", payload);

        verify(cacheWriteClient, times(1)).hmset(eq("client_business:ak_001"), anyMap());
    }

    @Test
    public void shouldRouteDirtyWordUpsertToDeleteThenSaddStr() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("members", Arrays.asList("bad_1", "bad_2"));

        cacheSyncService.syncUpsert("dirty_word", payload);

        verify(cacheWriteClient, times(1)).delete("dirty_word");
        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(cacheWriteClient, times(1)).saddStr(eq("dirty_word"), captor.capture());
        Assert.assertArrayEquals(new String[]{"bad_1", "bad_2"}, captor.getValue());
    }

    @Test
    public void shouldDeleteOnlyWhenDirtyWordMembersEmpty() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("members", Arrays.asList());

        cacheSyncService.syncUpsert("dirty_word", payload);

        verify(cacheWriteClient, times(1)).delete("dirty_word");
        verify(cacheWriteClient, never()).saddStr(eq("dirty_word"), org.mockito.ArgumentMatchers.<String[]>any());
        verify(cacheWriteClient, never()).sadd(eq("dirty_word"), org.mockito.ArgumentMatchers.<Map<String, Object>[]>any());
    }

    @Test
    public void shouldRouteClientChannelUpsertToDeleteThenSaddMap() {
        Map<String, Object> member = new HashMap<>();
        member.put("channelId", 2001L);
        member.put("clientChannelNumber", "1069");
        member.put("clientChannelWeight", 100);
        member.put("isAvailable", 0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);
        payload.put("members", Arrays.asList(member));

        cacheSyncService.syncUpsert("client_channel", payload);

        verify(cacheWriteClient, times(1)).delete("client_channel:1001");
        ArgumentCaptor<Map[]> memberCaptor = ArgumentCaptor.forClass(Map[].class);
        verify(cacheWriteClient, times(1)).sadd(eq("client_channel:1001"), memberCaptor.capture());
        Assert.assertEquals(1, memberCaptor.getValue().length);
        Assert.assertEquals(2001L, memberCaptor.getValue()[0].get("channelId"));
    }

    @Test
    public void shouldDeleteOnlyWhenClientTemplateMembersEmpty() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("signId", 2002L);
        payload.put("members", Arrays.asList());

        cacheSyncService.syncUpsert("client_template", payload);

        verify(cacheWriteClient, times(1)).delete("client_template:2002");
        verify(cacheWriteClient, never()).sadd(eq("client_template:2002"), org.mockito.ArgumentMatchers.<Map<String, Object>[]>any());
        verify(cacheWriteClient, never()).saddStr(eq("client_template:2002"), org.mockito.ArgumentMatchers.<String[]>any());
    }

    @Test
    public void shouldFallbackToStringSetWhenClientSignMembersAreStrings() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);
        payload.put("members", Arrays.asList("sign_a", "sign_b"));

        cacheSyncService.syncUpsert("client_sign", payload);

        verify(cacheWriteClient, times(1)).delete("client_sign:1001");
        verify(cacheWriteClient, times(1)).saddStr(eq("client_sign:1001"), org.mockito.ArgumentMatchers.<String[]>any());
        verify(cacheWriteClient, never()).sadd(eq("client_sign:1001"), org.mockito.ArgumentMatchers.<Map<String, Object>[]>any());
    }

    @Test
    public void shouldMapSpNumberToChannelNumberWhenUpsertChannel() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", 3001L);
        payload.put("spNumber", "1069");

        cacheSyncService.syncUpsert("channel", payload);

        ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cacheWriteClient, times(1)).hmset(eq("channel:3001"), payloadCaptor.capture());
        Assert.assertEquals("1069", payloadCaptor.getValue().get("channelNumber"));
    }

    @Test
    public void shouldRouteBlackDeleteToDeleteApiWithClientScopeKey() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);
        payload.put("mobile", "13800000000");

        cacheSyncService.syncDelete("black", payload);

        verify(cacheWriteClient, times(1)).delete("black:1001:13800000000");
    }

    @Test
    public void shouldDeleteSetKeyWhenSyncDeleteDirtyWord() {
        cacheSyncService.syncDelete("dirty_word", new HashMap<String, Object>());
        verify(cacheWriteClient, times(1)).delete("dirty_word");
    }

    @Test
    public void shouldSkipDeleteForClientBalanceBecauseOverwriteOnly() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);

        cacheSyncService.syncDelete("client_balance", payload);

        verify(cacheWriteClient, never()).delete("client_balance:1001");
    }

    @Test
    public void shouldRejectUnsupportedDomain() {
        try {
            cacheSyncService.syncUpsert("unknown_domain", new HashMap<String, Object>());
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertNotNull(ex.getCode());
        }
    }

    @Test
    public void shouldAllowAllManualRebuildForCurrentAllowedRange() {
        cacheSyncService.rebuildDomain("ALL");
    }

    @Test
    public void shouldRejectManualRebuildForNonMainlineDomain() {
        try {
            cacheSyncService.rebuildDomain("dirty_word");
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertNotNull(ex.getCode());
        }
    }

    @Test
    public void shouldRejectManualRebuildForBalanceDomainBeforeAllowed() {
        try {
            cacheSyncService.rebuildDomain("client_balance");
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertNotNull(ex.getCode());
        }
    }
}
