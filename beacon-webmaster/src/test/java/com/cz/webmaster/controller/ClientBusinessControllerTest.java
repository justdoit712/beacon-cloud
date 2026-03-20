package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceRechargeCommand;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.BalanceCommandService;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsRoleService;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientBusinessControllerTest {

    @After
    public void tearDown() {
        ThreadContext.unbindSubject();
    }

    @Test
    public void shouldDelegatePayToBalanceCommandService() {
        SmsRoleService roleService = Mockito.mock(SmsRoleService.class);
        ClientBusinessService clientBusinessService = Mockito.mock(ClientBusinessService.class);
        BalanceCommandService balanceCommandService = Mockito.mock(BalanceCommandService.class);

        ClientBusinessController controller = new ClientBusinessController(
                roleService,
                clientBusinessService,
                balanceCommandService
        );

        SmsUser currentUser = new SmsUser();
        currentUser.setId(99);

        Subject subject = Mockito.mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(currentUser);
        ThreadContext.bind(subject);

        Set<String> roleNames = new HashSet<>();
        roleNames.add(WebMasterConstants.ROOT);
        when(roleService.getRoleName(99)).thenReturn(roleNames);

        ClientBusiness target = new ClientBusiness();
        target.setId(1001L);
        target.setCorpname("corp_a");
        when(clientBusinessService.findById(1001L)).thenReturn(target);
        when(balanceCommandService.rechargeAndSync(any(ClientBalanceRechargeCommand.class)))
                .thenReturn(BalanceCommandResult.success(300L, null));

        ResultVO result = controller.pay(200L, 1001L);

        Assert.assertEquals(Integer.valueOf(0), result.getCode());
        org.mockito.ArgumentCaptor<ClientBalanceRechargeCommand> captor = org.mockito.ArgumentCaptor.forClass(ClientBalanceRechargeCommand.class);
        verify(balanceCommandService, times(1)).rechargeAndSync(captor.capture());
        Assert.assertEquals(Long.valueOf(1001L), captor.getValue().getClientId());
        Assert.assertEquals(Long.valueOf(200L), captor.getValue().getAmount());
        Assert.assertEquals(Long.valueOf(99L), captor.getValue().getOperatorId());
        verify(clientBusinessService, never()).update(any(ClientBusiness.class));
    }
}
