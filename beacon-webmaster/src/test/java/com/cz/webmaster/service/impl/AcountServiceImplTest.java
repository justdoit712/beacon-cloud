package com.cz.webmaster.service.impl;

import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceRechargeCommand;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.service.BalanceCommandService;
import com.cz.webmaster.service.ClientBusinessService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AcountServiceImplTest {

    @Test
    public void shouldDelegateRechargeWhenSavingAcount() {
        ClientBusinessService clientBusinessService = Mockito.mock(ClientBusinessService.class);
        BalanceCommandService balanceCommandService = Mockito.mock(BalanceCommandService.class);

        AcountServiceImpl service = new AcountServiceImpl();
        ReflectionTestUtils.setField(service, "clientBusinessService", clientBusinessService);
        ReflectionTestUtils.setField(service, "balanceCommandService", balanceCommandService);

        ClientBusiness clientBusiness = new ClientBusiness();
        clientBusiness.setId(1001L);
        clientBusiness.setCorpname("corp_a");

        when(clientBusinessService.findById(1001L)).thenReturn(clientBusiness);
        when(balanceCommandService.rechargeAndSync(any(ClientBalanceRechargeCommand.class)))
                .thenReturn(BalanceCommandResult.success(300L, null));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientid", 1001L);
        body.put("paidvalue", 200L);

        Assert.assertTrue(service.save(body, 99L));

        ArgumentCaptor<ClientBalanceRechargeCommand> captor = ArgumentCaptor.forClass(ClientBalanceRechargeCommand.class);
        verify(balanceCommandService, times(1)).rechargeAndSync(captor.capture());
        Assert.assertEquals(Long.valueOf(1001L), captor.getValue().getClientId());
        Assert.assertEquals(Long.valueOf(200L), captor.getValue().getAmount());
        Assert.assertEquals(Long.valueOf(99L), captor.getValue().getOperatorId());
    }

    @Test
    public void shouldReturnFalseWhenRechargeCommandFailsWhileSavingAcount() {
        ClientBusinessService clientBusinessService = Mockito.mock(ClientBusinessService.class);
        BalanceCommandService balanceCommandService = Mockito.mock(BalanceCommandService.class);

        AcountServiceImpl service = new AcountServiceImpl();
        ReflectionTestUtils.setField(service, "clientBusinessService", clientBusinessService);
        ReflectionTestUtils.setField(service, "balanceCommandService", balanceCommandService);

        ClientBusiness clientBusiness = new ClientBusiness();
        clientBusiness.setId(1001L);
        clientBusiness.setCorpname("corp_a");

        when(clientBusinessService.findById(1001L)).thenReturn(clientBusiness);
        when(balanceCommandService.rechargeAndSync(any(ClientBalanceRechargeCommand.class)))
                .thenReturn(BalanceCommandResult.failure(com.cz.webmaster.enums.BalanceCommandStatus.CLIENT_NOT_FOUND, null));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientid", 1001L);
        body.put("paidvalue", 200L);

        Assert.assertFalse(service.save(body, 99L));
        verify(balanceCommandService, times(1)).rechargeAndSync(any(ClientBalanceRechargeCommand.class));
        verify(clientBusinessService, never()).update(Mockito.any(ClientBusiness.class));
    }
}
