package com.cz.strategy.filter.impl;

import com.cz.common.constant.SmsConstant;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.util.ErrorSendMsgUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LimitOneHourStrategyFilterTest {

    @Test
    public void shouldSkipWhenNotCodeType() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        LimitOneHourStrategyFilter filter = new LimitOneHourStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);

        StandardSubmit submit = buildSubmit(SmsConstant.NOTIFY_TYPE);

        filter.strategy(submit);

        verify(cacheClient, never()).zadd(anyString(), anyLong(), any());
        verify(sendMsgUtil, never()).sendWriteLog(any(StandardSubmit.class));
        verify(sendMsgUtil, never()).sendPushReport(any(StandardSubmit.class));
    }

    @Test
    public void shouldRejectWhenMinuteLimitExceeded() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        LimitOneHourStrategyFilter filter = new LimitOneHourStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);

        StandardSubmit submit = buildSubmit(SmsConstant.CODE_TYPE);
        when(cacheClient.zadd(anyString(), anyLong(), any())).thenReturn(true);
        when(cacheClient.zRangeByScoreCount(anyString(), anyDouble(), anyDouble())).thenReturn(2);

        try {
            filter.strategy(submit);
            Assert.fail("expected StrategyException");
        } catch (StrategyException ex) {
            Assert.assertEquals(ExceptionEnums.ONE_MINUTE_LIMIT.getCode(), ex.getCode());
        }

        verify(cacheClient).zRemove(anyString(), anyString());
        verify(sendMsgUtil).sendWriteLog(submit);
        verify(sendMsgUtil).sendPushReport(submit);
    }

    @Test
    public void shouldPassWhenAllLimitCountsWithinThreshold() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        LimitOneHourStrategyFilter filter = new LimitOneHourStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);

        StandardSubmit submit = buildSubmit(SmsConstant.CODE_TYPE);
        when(cacheClient.zadd(anyString(), anyLong(), any())).thenReturn(true);
        when(cacheClient.zRangeByScoreCount(anyString(), anyDouble(), anyDouble())).thenReturn(1, 1, 1);

        filter.strategy(submit);

        verify(cacheClient, times(3)).zadd(anyString(), anyLong(), any());
        verify(cacheClient, times(3)).zRangeByScoreCount(anyString(), anyDouble(), anyDouble());
        verify(cacheClient, never()).zRemove(anyString(), anyString());
        verify(sendMsgUtil, never()).sendWriteLog(any(StandardSubmit.class));
        verify(sendMsgUtil, never()).sendPushReport(any(StandardSubmit.class));
    }

    private static StandardSubmit buildSubmit(int state) {
        StandardSubmit submit = new StandardSubmit();
        submit.setState(state);
        submit.setClientId(1001L);
        submit.setMobile("13800138000");
        submit.setApiKey("ak_001");
        submit.setSendTime(LocalDateTime.of(2026, 3, 27, 10, 0, 0));
        return submit;
    }
}
