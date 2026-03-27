package com.cz.strategy.filter.impl;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.common.vo.ResultVO;
import com.cz.strategy.client.InternalBalanceClient;
import com.cz.strategy.dto.InternalBalanceDebitRequest;
import com.cz.strategy.util.ErrorSendMsgUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FeeStrategyFilterTest {

    @Test
    public void shouldPassWhenDebitSuccess() {
        InternalBalanceClient internalBalanceClient = Mockito.mock(InternalBalanceClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        FeeStrategyFilter filter = new FeeStrategyFilter(internalBalanceClient, sendMsgUtil);
        ReflectionTestUtils.setField(filter, "internalBalanceToken", "token-01");

        StandardSubmit submit = buildSubmit();
        ResultVO<Void> success = new ResultVO<>();
        success.setCode(0);
        success.setMsg("ok");
        when(internalBalanceClient.debit(eq("token-01"), any(InternalBalanceDebitRequest.class))).thenReturn(success);

        filter.strategy(submit);

        ArgumentCaptor<InternalBalanceDebitRequest> requestCaptor = ArgumentCaptor.forClass(InternalBalanceDebitRequest.class);
        verify(internalBalanceClient).debit(eq("token-01"), requestCaptor.capture());
        InternalBalanceDebitRequest request = requestCaptor.getValue();
        Assert.assertEquals(Long.valueOf(1001L), request.getClientId());
        Assert.assertEquals(Long.valueOf(20L), request.getFee());
        Assert.assertEquals(Long.valueOf(-10000L), request.getAmountLimit());
        Assert.assertEquals("10001", request.getRequestId());
        verify(sendMsgUtil, never()).sendWriteLog(any(StandardSubmit.class));
        verify(sendMsgUtil, never()).sendPushReport(any(StandardSubmit.class));
    }

    @Test
    public void shouldThrowBalanceNotEnoughAndSendFailureMessages() {
        InternalBalanceClient internalBalanceClient = Mockito.mock(InternalBalanceClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        FeeStrategyFilter filter = new FeeStrategyFilter(internalBalanceClient, sendMsgUtil);

        StandardSubmit submit = buildSubmit();
        ResultVO<Void> fail = new ResultVO<>();
        fail.setCode(ExceptionEnums.BALANCE_NOT_ENOUGH.getCode());
        fail.setMsg(ExceptionEnums.BALANCE_NOT_ENOUGH.getMsg());
        when(internalBalanceClient.debit(Mockito.isNull(), any(InternalBalanceDebitRequest.class))).thenReturn(fail);

        try {
            filter.strategy(submit);
            Assert.fail("expected StrategyException");
        } catch (StrategyException ex) {
            Assert.assertEquals(ExceptionEnums.BALANCE_NOT_ENOUGH.getCode(), ex.getCode());
        }

        Assert.assertEquals(ExceptionEnums.BALANCE_NOT_ENOUGH.getMsg(), submit.getErrorMsg());
        verify(sendMsgUtil).sendWriteLog(submit);
        verify(sendMsgUtil).sendPushReport(submit);
    }

    @Test
    public void shouldThrowUnknownWhenDebitInvocationFails() {
        InternalBalanceClient internalBalanceClient = Mockito.mock(InternalBalanceClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        FeeStrategyFilter filter = new FeeStrategyFilter(internalBalanceClient, sendMsgUtil);

        StandardSubmit submit = buildSubmit();
        when(internalBalanceClient.debit(Mockito.isNull(), any(InternalBalanceDebitRequest.class)))
                .thenThrow(new RuntimeException("rpc timeout"));

        try {
            filter.strategy(submit);
            Assert.fail("expected StrategyException");
        } catch (StrategyException ex) {
            Assert.assertEquals(ExceptionEnums.UNKNOWN_ERROR.getCode(), ex.getCode());
        }

        verify(sendMsgUtil).sendWriteLog(submit);
        verify(sendMsgUtil).sendPushReport(submit);
    }

    private static StandardSubmit buildSubmit() {
        StandardSubmit submit = new StandardSubmit();
        submit.setSequenceId(10001L);
        submit.setClientId(1001L);
        submit.setFee(20L);
        submit.setApiKey("ak_001");
        submit.setUid("uid_001");
        return submit;
    }
}
