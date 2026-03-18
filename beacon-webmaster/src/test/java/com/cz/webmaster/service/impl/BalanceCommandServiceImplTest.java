package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.enums.BalanceCommandStatus;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BalanceCommandServiceImplTest {

    private ClientBusinessMapper mapper;
    private CacheSyncService cacheSyncService;
    private CacheSyncRuntimeExecutor runtimeExecutor;
    private BalanceCommandServiceImpl service;

    @Before
    public void setUp() {
        mapper = Mockito.mock(ClientBusinessMapper.class);
        cacheSyncService = Mockito.mock(CacheSyncService.class);
        runtimeExecutor = new CacheSyncRuntimeExecutor();
        service = new BalanceCommandServiceImpl(mapper, cacheSyncService, runtimeExecutor);
    }

    @Test
    public void shouldDebitAndRefreshBothDomains() {
        ClientBusiness latest = buildClientBusiness(1001L, "ak_1001", "90", (byte) 0);

        when(mapper.debitBalanceAtomic(1001L, 10L, -10000L, null)).thenReturn(1);
        when(mapper.selectByPrimaryKey(1001L)).thenReturn(latest);

        BalanceCommandResult result = service.debitAndSync(1001L, 10L, null, "req-1");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.SUCCESS, result.getStatus());
        Assert.assertEquals(Long.valueOf(90L), result.getBalance());
        Assert.assertEquals(Long.valueOf(-10000L), result.getAmountLimit());
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BALANCE, latest);
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latest);
    }

    @Test
    public void shouldReturnBalanceNotEnoughWhenDebitBlockedByLowerBound() {
        ClientBusiness existing = buildClientBusiness(1001L, "ak_1001", "5", (byte) 0);

        when(mapper.debitBalanceAtomic(1001L, 10L, 0L, null)).thenReturn(0);
        when(mapper.selectByPrimaryKey(1001L)).thenReturn(existing);

        BalanceCommandResult result = service.debitAndSync(1001L, 10L, 0L, "req-1");

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.BALANCE_NOT_ENOUGH, result.getStatus());
        Assert.assertEquals(Long.valueOf(0L), result.getAmountLimit());
        verify(cacheSyncService, never()).syncUpsert(eq(CacheDomainRegistry.CLIENT_BALANCE), any(ClientBusiness.class));
        verify(cacheSyncService, never()).syncUpsert(eq(CacheDomainRegistry.CLIENT_BUSINESS), any(ClientBusiness.class));
    }

    @Test
    public void shouldRechargeAndRefreshBothDomains() {
        ClientBusiness latest = buildClientBusiness(1001L, "ak_1001", "300", (byte) 0);

        when(mapper.rechargeBalanceAtomic(1001L, 200L, 99L)).thenReturn(1);
        when(mapper.selectByPrimaryKey(1001L)).thenReturn(latest);

        BalanceCommandResult result = service.rechargeAndSync(1001L, 200L, 99L, "req-2");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.SUCCESS, result.getStatus());
        Assert.assertEquals(Long.valueOf(300L), result.getBalance());
        Assert.assertNull(result.getAmountLimit());
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BALANCE, latest);
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latest);
    }

    @Test
    public void shouldAdjustAndRefreshBothDomains() {
        ClientBusiness latest = buildClientBusiness(1001L, "ak_1001", "120", (byte) 0);

        when(mapper.adjustBalanceAtomic(1001L, -30L, -1000L, 99L)).thenReturn(1);
        when(mapper.selectByPrimaryKey(1001L)).thenReturn(latest);

        BalanceCommandResult result = service.adjustAndSync(1001L, -30L, -1000L, 99L, "req-3");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(BalanceCommandStatus.SUCCESS, result.getStatus());
        Assert.assertEquals(Long.valueOf(120L), result.getBalance());
        Assert.assertEquals(Long.valueOf(-1000L), result.getAmountLimit());
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BALANCE, latest);
        verify(cacheSyncService, times(1)).syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latest);
    }

    private ClientBusiness buildClientBusiness(Long id, String apiKey, String balance, byte isDelete) {
        ClientBusiness clientBusiness = new ClientBusiness();
        clientBusiness.setId(id);
        clientBusiness.setApikey(apiKey);
        clientBusiness.setExtend4(balance);
        clientBusiness.setIsDelete(isDelete);
        return clientBusiness;
    }
}
