package com.cz.webmaster.service.impl;

import com.cz.common.exception.ApiException;
import com.cz.webmaster.client.BeaconCacheWriteClient;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.dto.CacheDeleteResultDTO;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.rebuild.CacheRebuildCoordinationSupport;
import com.cz.webmaster.rebuild.DomainRebuildLoader;
import com.cz.webmaster.rebuild.DomainRebuildLoaderRegistry;
import com.cz.webmaster.support.CacheKeyBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 主线域测试。
 *
 * <p>该测试类只覆盖当前主线 4 个域：
 * client_business、client_channel、channel、client_balance。</p>
 */
public class CacheSyncServiceImplMainlineTest {

    private BeaconCacheWriteClient cacheWriteClient;
    private CacheSyncServiceImpl cacheSyncService;
    private CacheRebuildCoordinationSupport cacheRebuildCoordinationSupport;

    @Before
    public void setUp() {
        cacheWriteClient = Mockito.mock(BeaconCacheWriteClient.class);
        cacheRebuildCoordinationSupport = Mockito.mock(CacheRebuildCoordinationSupport.class);
        Mockito.when(cacheRebuildCoordinationSupport.tryAcquireRebuildLock(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(cacheRebuildCoordinationSupport.releaseRebuildLock(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(cacheRebuildCoordinationSupport.consumeDirty(Mockito.anyString())).thenReturn(false);

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
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Arrays.asList(
                        stubLoader("client_business"),
                        stubLoader("client_channel"),
                        stubLoader("channel")
                )),
                cacheRebuildCoordinationSupport
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
    public void shouldRouteClientBalanceUpsertToHmsetWithTrueSourcePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);
        payload.put("balance", 520L);
        payload.put("corpname", "should_not_leak");

        cacheSyncService.syncUpsert("client_balance", payload);

        ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cacheWriteClient, times(1)).hmset(eq("client_balance:1001"), payloadCaptor.capture());
        Assert.assertEquals(1001L, payloadCaptor.getValue().get("clientId"));
        Assert.assertEquals(520L, payloadCaptor.getValue().get("balance"));
        Assert.assertFalse(payloadCaptor.getValue().containsKey("corpname"));
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
    public void shouldRejectClientBalanceUpsertWhenBalanceMissing() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);

        try {
            cacheSyncService.syncUpsert("client_balance", payload);
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertNotNull(ex.getCode());
        }
    }

    @Test
    public void shouldRejectClientChannelUpsertWhenMembersMissing() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);

        try {
            cacheSyncService.syncUpsert("client_channel", payload);
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertNotNull(ex.getCode());
        }
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
    public void shouldSkipDeleteForClientBalanceBecauseOverwriteOnly() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", 1001L);

        cacheSyncService.syncDelete("client_balance", payload);

        verify(cacheWriteClient, never()).delete("client_balance:1001");
    }

    @Test
    public void shouldAllowAllManualRebuildForCurrentAllowedRange() {
        CacheRebuildReport report = cacheSyncService.rebuildDomain("ALL");

        Assert.assertNotNull(report);
        Assert.assertEquals("ALL", report.getDomain());
        Assert.assertEquals("MANUAL", report.getTrigger());
        Assert.assertEquals("SUCCESS", report.getStatus());
        Assert.assertEquals(3, report.getReports().size());
        Assert.assertEquals("client_business", report.getReports().get(0).getDomain());
        Assert.assertEquals("client_channel", report.getReports().get(1).getDomain());
        Assert.assertEquals("channel", report.getReports().get(2).getDomain());
    }

    @Test
    public void shouldAllowBootRebuildWhenManualSwitchDisabled() {
        CacheSyncProperties properties = buildEnabledProperties();
        properties.getManual().setEnabled(false);
        properties.getBoot().setEnabled(true);

        CacheSyncServiceImpl service = new CacheSyncServiceImpl(
                properties,
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Collections.singletonList(stubLoader("channel"))),
                cacheRebuildCoordinationSupport
        );

        CacheRebuildReport report = service.rebuildBootDomain("channel");

        Assert.assertNotNull(report);
        Assert.assertEquals("BOOT", report.getTrigger());
        Assert.assertEquals("SUCCESS", report.getStatus());
    }

    @Test
    public void shouldRejectBootRebuildForBalanceDomainBeforeAllowed() {
        CacheSyncProperties properties = buildEnabledProperties();
        properties.getBoot().setEnabled(true);

        CacheSyncServiceImpl service = new CacheSyncServiceImpl(
                properties,
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Collections.singletonList(stubLoader("client_balance"))),
                cacheRebuildCoordinationSupport
        );

        try {
            service.rebuildBootDomain("client_balance");
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertNotNull(ex.getCode());
            Assert.assertTrue(ex.getMessage().contains("not allowed"));
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

    @Test
    public void shouldExcludeDomainWithoutRegisteredLoaderFromAllManualRebuild() {
        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
        properties.setEnabled(true);
        properties.getRuntime().setEnabled(true);
        properties.getManual().setEnabled(true);
        properties.getRedis().setNamespace("beacon:dev:beacon-cloud:cz:");
        properties.validate();

        CacheSyncServiceImpl service = new CacheSyncServiceImpl(
                properties,
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Arrays.asList(
                        stubLoader("client_business"),
                        stubLoader("channel")
                )),
                cacheRebuildCoordinationSupport
        );

        CacheRebuildReport report = service.rebuildDomain("ALL");

        Assert.assertEquals(2, report.getReports().size());
        Assert.assertEquals("client_business", report.getReports().get(0).getDomain());
        Assert.assertEquals("channel", report.getReports().get(1).getDomain());
    }

    @Test
    public void shouldRejectManualRebuildWhenLoaderNotRegistered() {
        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
        properties.setEnabled(true);
        properties.getRuntime().setEnabled(true);
        properties.getManual().setEnabled(true);
        properties.getRedis().setNamespace("beacon:dev:beacon-cloud:cz:");
        properties.validate();

        CacheSyncServiceImpl service = new CacheSyncServiceImpl(
                properties,
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Collections.singletonList(stubLoader("client_business"))),
                cacheRebuildCoordinationSupport
        );

        try {
            service.rebuildDomain("channel");
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertNotNull(ex.getCode());
            Assert.assertTrue(ex.getMessage().contains("loader not registered"));
        }
    }

    @Test
    public void shouldRejectManualRebuildWhenDomainBusy() {
        Mockito.when(cacheRebuildCoordinationSupport.tryAcquireRebuildLock(eq("channel"), Mockito.anyString())).thenReturn(false);

        try {
            cacheSyncService.rebuildDomain("channel");
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertTrue(ex.getMessage().contains("domain busy"));
        }
    }

    @Test
    public void shouldRejectBootRebuildWhenDomainBusy() {
        CacheSyncProperties properties = buildEnabledProperties();
        properties.getBoot().setEnabled(true);

        CacheSyncServiceImpl service = new CacheSyncServiceImpl(
                properties,
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Collections.singletonList(stubLoader("channel"))),
                cacheRebuildCoordinationSupport
        );
        Mockito.when(cacheRebuildCoordinationSupport.tryAcquireRebuildLock(eq("channel"), Mockito.anyString())).thenReturn(false);

        try {
            service.rebuildBootDomain("channel");
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertTrue(ex.getMessage().contains("boot reconcile domain busy"));
        }
        verify(cacheRebuildCoordinationSupport, never())
                .releaseRebuildLock(eq("channel"), Mockito.anyString());
    }

    @Test
    public void shouldMarkDirtyReplayWhenDirtyFlagExistsAfterRebuild() {
        Mockito.when(cacheRebuildCoordinationSupport.tryAcquireRebuildLock(eq("channel"), Mockito.anyString())).thenReturn(true);
        Mockito.when(cacheRebuildCoordinationSupport.consumeDirty("channel")).thenReturn(true, false);

        CacheRebuildReport report = cacheSyncService.rebuildDomain("channel");

        Assert.assertTrue(report.isDirtyReplay());
        Assert.assertTrue(report.getMessage().contains("dirty replay"));
        verify(cacheRebuildCoordinationSupport, times(1))
                .releaseRebuildLock(eq("channel"), Mockito.anyString());
    }

    @Test
    public void shouldRebuildClientBusinessByDeletingOldKeysAndWritingSnapshot() {
        CacheDeleteResultDTO deleteResult = new CacheDeleteResultDTO();
        deleteResult.setSuccessCount(1);
        deleteResult.setDeletedCount(1);
        Mockito.when(cacheWriteClient.keys("client_business:*", 1000))
                .thenReturn(new java.util.LinkedHashSet<>(Collections.singletonList("client_business:ak_old")));
        Mockito.when(cacheWriteClient.deleteBatch(Collections.singletonList("client_business:ak_old")))
                .thenReturn(deleteResult);

        CacheSyncServiceImpl service = new CacheSyncServiceImpl(
                buildEnabledProperties(),
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Collections.singletonList(singlePayloadLoader(
                        "client_business",
                        new java.util.LinkedHashMap<String, Object>() {{
                            put("apikey", "ak_new");
                            put("corpname", "corp_new");
                        }}
                ))),
                cacheRebuildCoordinationSupport
        );

        CacheRebuildReport report = service.rebuildDomain("client_business");

        Assert.assertEquals("SUCCESS", report.getStatus());
        Assert.assertEquals(1, report.getAttemptedKeys());
        Assert.assertEquals(1, report.getSuccessCount());
        Assert.assertEquals(0, report.getFailCount());
        verify(cacheWriteClient, times(1)).deleteBatch(Collections.singletonList("client_business:ak_old"));
        verify(cacheWriteClient, times(1)).hmset(eq("client_business:ak_new"), anyMap());
    }

    @Test
    public void shouldRecordFailedKeyWhenRebuildItemFails() {
        CacheSyncServiceImpl service = new CacheSyncServiceImpl(
                buildEnabledProperties(),
                new CacheKeyBuilder(),
                cacheWriteClient,
                new ObjectMapper(),
                new DomainRebuildLoaderRegistry(Collections.singletonList(singlePayloadLoader(
                        "client_business",
                        new java.util.LinkedHashMap<String, Object>() {{
                            put("corpname", "corp_without_api_key");
                        }}
                ))),
                cacheRebuildCoordinationSupport
        );

        CacheRebuildReport report = service.rebuildDomain("client_business");

        Assert.assertEquals("FAIL", report.getStatus());
        Assert.assertEquals(1, report.getAttemptedKeys());
        Assert.assertEquals(0, report.getSuccessCount());
        Assert.assertEquals(1, report.getFailCount());
        Assert.assertEquals(1, report.getFailedKeys().size());
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

    private DomainRebuildLoader stubLoader(String domainCode) {
        return new DomainRebuildLoader() {
            @Override
            public String domainCode() {
                return domainCode;
            }

            @Override
            public List<Object> loadSnapshot() {
                return Collections.emptyList();
            }
        };
    }

    private DomainRebuildLoader singlePayloadLoader(String domainCode, Object payload) {
        return new DomainRebuildLoader() {
            @Override
            public String domainCode() {
                return domainCode;
            }

            @Override
            public List<Object> loadSnapshot() {
                return Collections.singletonList(payload);
            }
        };
    }

    private CacheSyncProperties buildEnabledProperties() {
        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
        properties.setEnabled(true);
        properties.getRuntime().setEnabled(true);
        properties.getManual().setEnabled(true);
        properties.getRedis().setNamespace("beacon:dev:beacon-cloud:cz:");
        properties.validate();
        return properties;
    }
}
