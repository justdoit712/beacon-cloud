package com.cz.webmaster.service.impl;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.mapper.ClientTemplateMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientTemplateServiceImplTest {

    private ClientTemplateMapper clientTemplateMapper;
    private CacheSyncService cacheSyncService;
    private ClientTemplateServiceImpl service;

    @Before
    public void setUp() {
        clientTemplateMapper = Mockito.mock(ClientTemplateMapper.class);
        cacheSyncService = Mockito.mock(CacheSyncService.class);
        service = new ClientTemplateServiceImpl(clientTemplateMapper, cacheSyncService, new CacheSyncRuntimeExecutor());
    }

    @Test
    public void shouldSyncClientTemplateSetAfterSave() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 7001L);
        body.put("signId", 2002L);
        body.put("templateText", "验证码#code#");
        body.put("templateType", 0);
        body.put("templateState", 2);
        body.put("useId", 0);
        body.put("useWeb", "https://example.com");

        Map<String, Object> member = new LinkedHashMap<>();
        member.put("id", 7001L);
        member.put("templateText", "验证码#code#");

        when(clientTemplateMapper.insert(any())).thenReturn(1);
        when(clientTemplateMapper.findActiveMembersBySignId(2002L)).thenReturn(Collections.singletonList(member));

        Assert.assertTrue(service.save(body, 1L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.CLIENT_TEMPLATE), payloadCaptor.capture());
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        Assert.assertEquals(2002L, payload.get("signId"));
        Assert.assertEquals(Collections.singletonList(member), payload.get("members"));
    }

    @Test
    public void shouldRebuildOldAndNewSignTemplateSetWhenSignIdChanges() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("id", 7001L);
        before.put("signId", 2002L);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 7001L);
        body.put("signId", 3003L);
        body.put("templateText", "通知模板");
        body.put("templateType", 1);
        body.put("templateState", 2);
        body.put("useId", 1);
        body.put("useWeb", "app");

        when(clientTemplateMapper.findById(7001L)).thenReturn(before);
        when(clientTemplateMapper.update(any())).thenReturn(1);
        when(clientTemplateMapper.findActiveMembersBySignId(2002L)).thenReturn(Collections.emptyList());
        when(clientTemplateMapper.findActiveMembersBySignId(3003L)).thenReturn(Arrays.asList(body));

        Assert.assertTrue(service.update(body, 1L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(2)).syncUpsert(eq(CacheDomainRegistry.CLIENT_TEMPLATE), payloadCaptor.capture());
        Assert.assertEquals(2002L, ((Map<String, Object>) payloadCaptor.getAllValues().get(0)).get("signId"));
        Assert.assertEquals(3003L, ((Map<String, Object>) payloadCaptor.getAllValues().get(1)).get("signId"));
    }
}
