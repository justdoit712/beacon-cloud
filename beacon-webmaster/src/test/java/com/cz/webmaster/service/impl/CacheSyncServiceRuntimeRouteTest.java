package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.ClientChannel;
import com.cz.webmaster.enums.BalanceCommandStatus;
import com.cz.webmaster.mapper.ChannelMapper;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.mapper.ClientChannelMapper;
import com.cz.webmaster.service.BalanceCommandService;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CacheSyncServiceRuntimeRouteTest {

    private CacheSyncService cacheSyncService;
    private CacheSyncRuntimeExecutor runtimeExecutor;

    @Before
    public void setUp() {
        cacheSyncService = Mockito.mock(CacheSyncService.class);
        runtimeExecutor = new CacheSyncRuntimeExecutor();
    }

    @Test
    public void shouldSyncClientBusinessAfterSave() {
        ClientBusinessMapper mapper = Mockito.mock(ClientBusinessMapper.class);
        ClientBusinessServiceImpl service = new ClientBusinessServiceImpl();
        ReflectionTestUtils.setField(service, "clientBusinessMapper", mapper);
        ReflectionTestUtils.setField(service, "cacheSyncService", cacheSyncService);
        ReflectionTestUtils.setField(service, "cacheSyncRuntimeExecutor", runtimeExecutor);

        ClientBusiness input = new ClientBusiness();
        input.setId(1001L);
        input.setApikey("ak_1001");

        ClientBusiness latest = new ClientBusiness();
        latest.setId(1001L);
        latest.setApikey("ak_1001");
        latest.setCorpname("corp_a");

        when(mapper.insertSelective(any(ClientBusiness.class))).thenReturn(1);
        when(mapper.selectByPrimaryKey(1001L)).thenReturn(latest);

        Assert.assertTrue(service.save(input));
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latest);
    }

    @Test
    public void shouldDeleteOldClientBusinessKeyWhenApiKeyChanges() {
        ClientBusinessMapper mapper = Mockito.mock(ClientBusinessMapper.class);
        ClientBusinessServiceImpl service = new ClientBusinessServiceImpl();
        ReflectionTestUtils.setField(service, "clientBusinessMapper", mapper);
        ReflectionTestUtils.setField(service, "cacheSyncService", cacheSyncService);
        ReflectionTestUtils.setField(service, "cacheSyncRuntimeExecutor", runtimeExecutor);

        ClientBusiness before = new ClientBusiness();
        before.setId(1001L);
        before.setApikey("ak_old");

        ClientBusiness latest = new ClientBusiness();
        latest.setId(1001L);
        latest.setApikey("ak_new");
        latest.setCorpname("corp_a");

        ClientBusiness update = new ClientBusiness();
        update.setId(1001L);
        update.setApikey("ak_new");

        when(mapper.selectByPrimaryKey(1001L)).thenReturn(before, latest);
        when(mapper.updateByPrimaryKeySelective(any(ClientBusiness.class))).thenReturn(1);

        Assert.assertTrue(service.update(update));
        verify(cacheSyncService, times(1)).syncDelete(CacheDomainRegistry.CLIENT_BUSINESS, before);
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latest);
    }

    @Test
    public void shouldRejectClientBusinessUpdateWhenExtend4IsPresent() {
        ClientBusinessMapper mapper = Mockito.mock(ClientBusinessMapper.class);
        ClientBusinessServiceImpl service = new ClientBusinessServiceImpl();
        ReflectionTestUtils.setField(service, "clientBusinessMapper", mapper);
        ReflectionTestUtils.setField(service, "cacheSyncService", cacheSyncService);
        ReflectionTestUtils.setField(service, "cacheSyncRuntimeExecutor", runtimeExecutor);

        ClientBusiness update = new ClientBusiness();
        update.setId(1001L);
        update.setExtend4("123");

        Assert.assertFalse(service.update(update));
        verify(mapper, times(0)).selectByPrimaryKey(any(Long.class));
        verify(mapper, times(0)).updateByPrimaryKeySelective(any(ClientBusiness.class));
        verify(cacheSyncService, times(0)).syncDelete(eq(CacheDomainRegistry.CLIENT_BUSINESS), any());
        verify(cacheSyncService, times(0)).syncUpsert(eq(CacheDomainRegistry.CLIENT_BUSINESS), any());
    }

    @Test
    public void shouldSyncClientBusinessDeleteAfterDeleteBatch() {
        ClientBusinessMapper mapper = Mockito.mock(ClientBusinessMapper.class);
        ClientBusinessServiceImpl service = new ClientBusinessServiceImpl();
        ReflectionTestUtils.setField(service, "clientBusinessMapper", mapper);
        ReflectionTestUtils.setField(service, "cacheSyncService", cacheSyncService);
        ReflectionTestUtils.setField(service, "cacheSyncRuntimeExecutor", runtimeExecutor);

        ClientBusiness first = new ClientBusiness();
        first.setId(1001L);
        first.setApikey("ak_1001");

        ClientBusiness second = new ClientBusiness();
        second.setId(1002L);
        second.setApikey("ak_1002");

        when(mapper.selectByPrimaryKey(1001L)).thenReturn(first);
        when(mapper.selectByPrimaryKey(1002L)).thenReturn(second);
        when(mapper.updateByPrimaryKeySelective(any(ClientBusiness.class))).thenReturn(1);

        Assert.assertTrue(service.deleteBatch(Arrays.asList(1001L, 1002L)));
        verify(cacheSyncService, times(1)).syncDelete(CacheDomainRegistry.CLIENT_BUSINESS, first);
        verify(cacheSyncService, times(1)).syncDelete(CacheDomainRegistry.CLIENT_BUSINESS, second);
    }

    @Test
    public void shouldSyncClientChannelAfterUpdate() {
        ClientChannelMapper mapper = Mockito.mock(ClientChannelMapper.class);
        ClientChannelServiceImpl service = new ClientChannelServiceImpl();
        ReflectionTestUtils.setField(service, "clientChannelMapper", mapper);
        ReflectionTestUtils.setField(service, "cacheSyncService", cacheSyncService);
        ReflectionTestUtils.setField(service, "cacheSyncRuntimeExecutor", runtimeExecutor);

        ClientChannel before = new ClientChannel();
        before.setId(2001L);
        before.setClientId(3001L);
        before.setChannelId(4001L);
        before.setExtendNumber("001");
        before.setPrice(3L);

        ClientChannel latest = new ClientChannel();
        latest.setId(2001L);
        latest.setClientId(3001L);
        latest.setChannelId(4002L);
        latest.setExtendNumber("002");
        latest.setPrice(5L);

        when(mapper.findById(2001L)).thenReturn(before, latest);
        when(mapper.updateById(any(ClientChannel.class))).thenReturn(1);
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("channelId", 4002L);
        member.put("clientChannelWeight", 100);
        member.put("clientChannelNumber", "1069");
        member.put("isAvailable", 0);
        when(mapper.findRouteMembersByClientIds(eq(Collections.singletonList(3001L))))
                .thenReturn(Collections.singletonList(member));

        ClientChannel update = new ClientChannel();
        update.setId(2001L);
        update.setClientId(3001L);
        update.setChannelId(4002L);
        update.setExtendNumber("002");
        update.setPrice(5L);

        Assert.assertTrue(service.update(update));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(1))
                .syncUpsert(eq(CacheDomainRegistry.CLIENT_CHANNEL), payloadCaptor.capture());

        Object payload = payloadCaptor.getValue();
        Assert.assertTrue(payload instanceof Map);
        Map<String, Object> payloadMap = (Map<String, Object>) payload;
        Assert.assertEquals(3001L, payloadMap.get("clientId"));
        Assert.assertTrue(payloadMap.get("members") instanceof List);
    }

    @Test
    public void shouldSyncChannelDeleteAfterDeleteBatch() {
        ChannelMapper mapper = Mockito.mock(ChannelMapper.class);
        ChannelServiceImpl service = new ChannelServiceImpl();
        ReflectionTestUtils.setField(service, "channelMapper", mapper);
        ReflectionTestUtils.setField(service, "cacheSyncService", cacheSyncService);
        ReflectionTestUtils.setField(service, "cacheSyncRuntimeExecutor", runtimeExecutor);

        when(mapper.deleteBatch(any(List.class), any(java.util.Date.class), eq(1L))).thenReturn(1);

        Assert.assertTrue(service.deleteBatch(Arrays.asList(7001L, 7002L), 1L));
        verify(cacheSyncService, times(1)).syncDelete(CacheDomainRegistry.CHANNEL, 7001L);
        verify(cacheSyncService, times(1)).syncDelete(CacheDomainRegistry.CHANNEL, 7002L);
    }

    @Test
    public void shouldDelegateDebitToBalanceCommandService() {
        BalanceCommandService balanceCommandService = Mockito.mock(BalanceCommandService.class);
        when(balanceCommandService.debitAndSync(9001L, 10L, -10000L, "req-1"))
                .thenReturn(BalanceCommandResult.success(90L, -10000L));

        ClientBalanceDebitServiceImpl service = new ClientBalanceDebitServiceImpl(balanceCommandService);

        BalanceCommandResult result = service.debitAndSync(9001L, 10L, -10000L, "req-1");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(Long.valueOf(90L), result.getBalance());
        Assert.assertEquals(BalanceCommandStatus.SUCCESS, result.getStatus());
    }
}
