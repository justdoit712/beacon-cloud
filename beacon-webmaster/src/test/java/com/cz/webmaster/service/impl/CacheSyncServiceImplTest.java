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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * CacheSyncServiceImpl 骨架单元测试。
 * <p>
 * 重点验证：域路由 -> Key 构建 -> 写删客户端调用 是否符合预期。
 */
public class CacheSyncServiceImplTest {

    private BeaconCacheWriteClient cacheWriteClient;
    private CacheSyncServiceImpl cacheSyncService;

    @Before
    public void setUp() {
        cacheWriteClient = Mockito.mock(BeaconCacheWriteClient.class);

        CacheSyncProperties properties = new CacheSyncProperties();
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
        payload.put("members", Arrays.asList("违法词", "营销词"));

        cacheSyncService.syncUpsert("dirty_word", payload);

        verify(cacheWriteClient, times(1)).delete("dirty_word");
        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(cacheWriteClient, times(1)).saddStr(eq("dirty_word"), captor.capture());
        Assert.assertArrayEquals(new String[]{"违法词", "营销词"}, captor.getValue());
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
}

