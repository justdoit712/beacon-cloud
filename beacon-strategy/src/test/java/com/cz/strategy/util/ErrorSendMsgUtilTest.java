package com.cz.strategy.util;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.constant.SmsConstant;
import com.cz.common.model.StandardReport;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.CacheFacade;
import com.cz.strategy.client.dto.ClientBusinessSnapshot;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ErrorSendMsgUtilTest {

    @Test
    public void shouldSendWriteLogAndMarkSubmitFailed() {
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        CacheFacade cacheFacade = Mockito.mock(CacheFacade.class);

        ErrorSendMsgUtil util = new ErrorSendMsgUtil();
        ReflectionTestUtils.setField(util, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(util, "cacheFacade", cacheFacade);

        StandardSubmit submit = new StandardSubmit();

        util.sendWriteLog(submit);

        Assert.assertEquals(SmsConstant.REPORT_FAIL, submit.getReportState());
        verify(rabbitTemplate).convertAndSend(RabbitMQConstants.SMS_WRITE_LOG, submit);
    }

    @Test
    public void shouldSendPushReportWhenCallbackEnabledAndUrlPresent() {
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        CacheFacade cacheFacade = Mockito.mock(CacheFacade.class);

        ErrorSendMsgUtil util = new ErrorSendMsgUtil();
        ReflectionTestUtils.setField(util, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(util, "cacheFacade", cacheFacade);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");
        submit.setSequenceId(1L);
        submit.setMobile("13800000000");

        when(cacheFacade.getClientBusinessSnapshot("ak_001"))
                .thenReturn(new ClientBusinessSnapshot("ak_001", 1, "https://callback.example.com/report", null));
        when(cacheFacade.markPushReportDispatched(1L)).thenReturn(true);

        util.sendPushReport(submit);

        ArgumentCaptor<StandardReport> reportCaptor = ArgumentCaptor.forClass(StandardReport.class);
        verify(rabbitTemplate).convertAndSend(Mockito.eq(RabbitMQConstants.SMS_PUSH_REPORT), reportCaptor.capture());

        StandardReport report = reportCaptor.getValue();
        Assert.assertEquals(submit.getSequenceId(), report.getSequenceId());
        Assert.assertEquals(Integer.valueOf(1), report.getIsCallback());
        Assert.assertEquals("https://callback.example.com/report", report.getCallbackUrl());
    }

    @Test
    public void shouldSkipPushReportWhenAlreadyDispatched() {
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        CacheFacade cacheFacade = Mockito.mock(CacheFacade.class);

        ErrorSendMsgUtil util = new ErrorSendMsgUtil();
        ReflectionTestUtils.setField(util, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(util, "cacheFacade", cacheFacade);

        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey("ak_001");
        submit.setSequenceId(1L);

        when(cacheFacade.getClientBusinessSnapshot("ak_001"))
                .thenReturn(new ClientBusinessSnapshot("ak_001", 1, "https://callback.example.com/report", null));
        when(cacheFacade.markPushReportDispatched(1L)).thenReturn(false);

        util.sendPushReport(submit);

        verify(rabbitTemplate, Mockito.never()).convertAndSend(Mockito.eq(RabbitMQConstants.SMS_PUSH_REPORT), Mockito.any(StandardReport.class));
    }
}
