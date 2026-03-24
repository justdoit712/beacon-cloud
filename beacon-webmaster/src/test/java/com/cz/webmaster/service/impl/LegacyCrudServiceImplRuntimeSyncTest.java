package com.cz.webmaster.service.impl;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LegacyCrudServiceImplRuntimeSyncTest {

    private CacheSyncService cacheSyncService;
    private LegacyCrudServiceImpl legacyCrudService;

    @Before
    public void setUp() {
        cacheSyncService = Mockito.mock(CacheSyncService.class);
        legacyCrudService = new LegacyCrudServiceImpl();
        ReflectionTestUtils.setField(legacyCrudService, "cacheSyncService", cacheSyncService);
        ReflectionTestUtils.setField(legacyCrudService, "cacheSyncRuntimeExecutor", new CacheSyncRuntimeExecutor());
    }

    @Test
    public void shouldSyncBlackOnSaveAndUpdate() {
        Map<String, Object> saveBody = new LinkedHashMap<>();
        saveBody.put("id", 101L);
        saveBody.put("mobile", "13800000001");
        saveBody.put("clientId", 1001L);
        boolean saved = legacyCrudService.save("black", saveBody, 1L);
        Assert.assertTrue(saved);
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.BLACK), any());

        Mockito.reset(cacheSyncService);
        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("id", 101L);
        updateBody.put("mobile", "13800000002");
        boolean updated = legacyCrudService.update("black", updateBody, 1L);
        Assert.assertTrue(updated);
        verify(cacheSyncService, times(1)).syncDelete(eq(CacheDomainRegistry.BLACK), any());
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.BLACK), any());
    }

    @Test
    public void shouldRebuildDirtyWordSetAfterSaveAndDelete() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("id", 201L);
        row1.put("dirtyword", "spam_1");
        Assert.assertTrue(legacyCrudService.save("message", row1, 1L));

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", 202L);
        row2.put("dirtyword", "spam_2");
        Assert.assertTrue(legacyCrudService.save("message", row2, 1L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(2)).syncUpsert(eq(CacheDomainRegistry.DIRTY_WORD), payloadCaptor.capture());
        Object secondPayload = payloadCaptor.getAllValues().get(1);
        Assert.assertTrue(secondPayload instanceof Map);
        Map<String, Object> secondPayloadMap = (Map<String, Object>) secondPayload;
        Assert.assertTrue(secondPayloadMap.get("members") instanceof List);
        List<String> members = (List<String>) secondPayloadMap.get("members");
        Assert.assertEquals(2, members.size());
        Assert.assertTrue(members.contains("spam_1"));
        Assert.assertTrue(members.contains("spam_2"));

        Mockito.reset(cacheSyncService);
        Assert.assertTrue(legacyCrudService.deleteBatch("message", Arrays.asList(201L)));
        ArgumentCaptor<Object> deletePayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.DIRTY_WORD), deletePayloadCaptor.capture());
        Map<String, Object> payload = (Map<String, Object>) deletePayloadCaptor.getValue();
        Assert.assertEquals(Arrays.asList("spam_2"), payload.get("members"));
    }

    @Test
    public void shouldSyncTransferWhenSearchParamsHasMobileAndValue() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 301L);
        body.put("transferNumber", "13800000003");
        body.put("nowIsp", "2");
        Assert.assertTrue(legacyCrudService.save("searchparams", body, 1L));
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.TRANSFER), any());
    }

    @Test
    public void shouldSkipTransferSyncWhenSearchParamsLacksTransferFields() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 302L);
        body.put("name", "queryName");
        body.put("cloum", "queryColumn");
        Assert.assertTrue(legacyCrudService.save("searchparams", body, 1L));
        verify(cacheSyncService, never()).syncUpsert(eq(CacheDomainRegistry.TRANSFER), any());
        verify(cacheSyncService, never()).syncDelete(eq(CacheDomainRegistry.TRANSFER), any());
    }
}
