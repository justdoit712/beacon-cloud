package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.entity.ClientBalance;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.enums.BalanceCommandStatus;
import com.cz.webmaster.mapper.ClientBalanceMapper;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BalanceCommandServiceImplTest {

    private ClientBalanceMapper clientBalanceMapper;
    private ClientBusinessMapper clientBusinessMapper;
    private CacheSyncService cacheSyncService;
    private CacheSyncRuntimeExecutor runtimeExecutor;
    private BalanceCommandServiceImpl service;

    @Before
    public void setUp() {
        clientBalanceMapper = Mockito.mock(ClientBalanceMapper.class);
        clientBusinessMapper = Mockito.mock(ClientBusinessMapper.class);
        cacheSyncService = Mockito.mock(CacheSyncService.class);
        runtimeExecutor = new CacheSyncRuntimeExecutor();
        service = new BalanceCommandServiceImpl(clientBalanceMapper, clientBusinessMapper, cacheSyncService, runtimeExecutor);
    }

    @Test
    public void shouldDebitAndRefreshBothDomains() {
        ClientBalance latestBalance = buildClientBalance(2001L, 1001L, 90L, (byte) 0);
        ClientBusiness latestBusiness = buildClientBusiness(1001L, "ak_1001", (byte) 0);

        when(clientBalanceMapper.debitBalanceAtomic(1001L, 10L, -10000L, null)).thenReturn(1);
        when(clientBalanceMapper.selectByClientId(1001L)).thenReturn(latestBalance);
        when(clientBusinessMapper.selectByPrimaryKey(1001L)).thenReturn(latestBusiness);

        BalanceCommandResult result = service.debitAndSync(1001L, 10L, null, "req-1");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.SUCCESS, result.getStatus());
        Assert.assertEquals(Long.valueOf(90L), result.getBalance());
        Assert.assertEquals(Long.valueOf(-10000L), result.getAmountLimit());

        ArgumentCaptor<Object> balancePayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.CLIENT_BALANCE), balancePayloadCaptor.capture());
        Assert.assertTrue(balancePayloadCaptor.getValue() instanceof Map);
        Map<?, ?> balancePayload = (Map<?, ?>) balancePayloadCaptor.getValue();
        Assert.assertEquals(1001L, balancePayload.get("clientId"));
        Assert.assertEquals(90L, balancePayload.get("balance"));

        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latestBusiness);
    }

    @Test
    public void shouldReturnBalanceNotEnoughWhenDebitBlockedByLowerBound() {
        ClientBalance existingBalance = buildClientBalance(2001L, 1001L, 5L, (byte) 0);

        when(clientBalanceMapper.debitBalanceAtomic(1001L, 10L, 0L, null)).thenReturn(0);
        when(clientBalanceMapper.selectByClientId(1001L)).thenReturn(existingBalance);

        BalanceCommandResult result = service.debitAndSync(1001L, 10L, 0L, "req-1");

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.BALANCE_NOT_ENOUGH, result.getStatus());
        Assert.assertEquals(Long.valueOf(0L), result.getAmountLimit());
        verify(cacheSyncService, never()).syncUpsert(eq(CacheDomainRegistry.CLIENT_BALANCE), any());
        verify(cacheSyncService, never()).syncUpsert(eq(CacheDomainRegistry.CLIENT_BUSINESS), any(ClientBusiness.class));
    }

    @Test
    public void shouldRechargeAndRefreshBothDomains() {
        ClientBalance latestBalance = buildClientBalance(2001L, 1001L, 300L, (byte) 0);
        ClientBusiness latestBusiness = buildClientBusiness(1001L, "ak_1001", (byte) 0);

        when(clientBalanceMapper.rechargeBalanceAtomic(1001L, 200L, 99L)).thenReturn(1);
        when(clientBalanceMapper.selectByClientId(1001L)).thenReturn(latestBalance);
        when(clientBusinessMapper.selectByPrimaryKey(1001L)).thenReturn(latestBusiness);

        BalanceCommandResult result = service.rechargeAndSync(1001L, 200L, 99L, "req-2");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.SUCCESS, result.getStatus());
        Assert.assertEquals(Long.valueOf(300L), result.getBalance());
        Assert.assertNull(result.getAmountLimit());

        ArgumentCaptor<Object> rechargePayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.CLIENT_BALANCE), rechargePayloadCaptor.capture());
        Assert.assertTrue(rechargePayloadCaptor.getValue() instanceof Map);
        Map<?, ?> rechargePayload = (Map<?, ?>) rechargePayloadCaptor.getValue();
        Assert.assertEquals(1001L, rechargePayload.get("clientId"));
        Assert.assertEquals(300L, rechargePayload.get("balance"));

        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latestBusiness);
    }

    @Test
    public void shouldAdjustAndRefreshBothDomains() {
        ClientBalance latestBalance = buildClientBalance(2001L, 1001L, 120L, (byte) 0);
        ClientBusiness latestBusiness = buildClientBusiness(1001L, "ak_1001", (byte) 0);

        when(clientBalanceMapper.adjustBalanceAtomic(1001L, -30L, -1000L, 99L)).thenReturn(1);
        when(clientBalanceMapper.selectByClientId(1001L)).thenReturn(latestBalance);
        when(clientBusinessMapper.selectByPrimaryKey(1001L)).thenReturn(latestBusiness);

        BalanceCommandResult result = service.adjustAndSync(1001L, -30L, -1000L, 99L, "req-3");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.SUCCESS, result.getStatus());
        Assert.assertEquals(Long.valueOf(120L), result.getBalance());
        Assert.assertEquals(Long.valueOf(-1000L), result.getAmountLimit());

        ArgumentCaptor<Object> adjustPayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(cacheSyncService, times(1)).syncUpsert(eq(CacheDomainRegistry.CLIENT_BALANCE), adjustPayloadCaptor.capture());
        Assert.assertTrue(adjustPayloadCaptor.getValue() instanceof Map);
        Map<?, ?> adjustPayload = (Map<?, ?>) adjustPayloadCaptor.getValue();
        Assert.assertEquals(1001L, adjustPayload.get("clientId"));
        Assert.assertEquals(120L, adjustPayload.get("balance"));

        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latestBusiness);
    }

    private ClientBalance buildClientBalance(Long id, Long clientId, Long balance, byte isDelete) {
        ClientBalance clientBalance = new ClientBalance();
        clientBalance.setId(id);
        clientBalance.setClientId(clientId);
        clientBalance.setBalance(balance);
        clientBalance.setIsDelete(isDelete);
        return clientBalance;
    }

    private ClientBusiness buildClientBusiness(Long id, String apiKey, byte isDelete) {
        ClientBusiness clientBusiness = new ClientBusiness();
        clientBusiness.setId(id);
        clientBusiness.setApikey(apiKey);
        clientBusiness.setIsDelete(isDelete);
        return clientBusiness;
    }
}
