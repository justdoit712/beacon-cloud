package com.cz.webmaster.controller;

import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceDebitCommand;
import com.cz.webmaster.dto.InternalBalanceDebitRequest;
import com.cz.webmaster.service.BalanceCommandService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.validation.BeanPropertyBindingResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InternalBalanceControllerTest {

    @Test
    public void shouldDelegateDebitToBalanceCommandServiceWithCommandObject() {
        BalanceCommandService balanceCommandService = Mockito.mock(BalanceCommandService.class);
        InternalBalanceController controller = new InternalBalanceController(balanceCommandService);

        InternalBalanceDebitRequest request = new InternalBalanceDebitRequest();
        request.setClientId(1001L);
        request.setFee(10L);
        request.setAmountLimit(-10000L);
        request.setRequestId("req-1");

        when(balanceCommandService.debitAndSync(any(ClientBalanceDebitCommand.class)))
                .thenReturn(BalanceCommandResult.success(90L, -10000L));

        ResultVO result = controller.debit(request, new BeanPropertyBindingResult(request, "request"), null);

        Assert.assertEquals(Integer.valueOf(0), result.getCode());
        ArgumentCaptor<ClientBalanceDebitCommand> captor = ArgumentCaptor.forClass(ClientBalanceDebitCommand.class);
        verify(balanceCommandService, times(1)).debitAndSync(captor.capture());
        Assert.assertEquals(Long.valueOf(1001L), captor.getValue().getClientId());
        Assert.assertEquals(Long.valueOf(10L), captor.getValue().getFee());
        Assert.assertEquals(Long.valueOf(-10000L), captor.getValue().getAmountLimit());
        Assert.assertEquals("req-1", captor.getValue().getRequestId());
    }
}
