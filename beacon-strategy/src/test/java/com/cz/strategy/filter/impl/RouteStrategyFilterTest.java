package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.CacheFacade;
import com.cz.strategy.client.dto.ChannelInfo;
import com.cz.strategy.client.dto.ClientChannelBinding;
import com.cz.strategy.util.ErrorSendMsgUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RouteStrategyFilterTest {

    @Test
    public void shouldSendToGatewayQueueWhenAvailableChannelFound() {
        CacheFacade cacheFacade = Mockito.mock(CacheFacade.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        AmqpAdmin amqpAdmin = Mockito.mock(AmqpAdmin.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);

        RouteStrategyFilter filter = new RouteStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheFacade", cacheFacade);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);
        ReflectionTestUtils.setField(filter, "amqpAdmin", amqpAdmin);
        ReflectionTestUtils.setField(filter, "rabbitTemplate", rabbitTemplate);

        StandardSubmit submit = new StandardSubmit();
        submit.setClientId(1001L);
        submit.setOperatorId(1);

        when(cacheFacade.getClientChannelBindings(1001L))
                .thenReturn(Collections.singletonList(new ClientChannelBinding(2001L, 10, 0, "01")));
        when(cacheFacade.getChannelInfo(2001L)).thenReturn(new ChannelInfo(2001L, 0, 0, "1069"));

        filter.strategy(submit);

        Assert.assertEquals(Long.valueOf(2001L), submit.getChannelId());
        Assert.assertEquals("106901", submit.getSrcNumber());
        verify(amqpAdmin).declareQueue(any(Queue.class));
        verify(rabbitTemplate).convertAndSend(RabbitMQConstants.SMS_GATEWAY + "2001", submit);
    }

    @Test
    public void shouldThrowNoChannelAndSendFailureMessagesWhenNoAvailableChannel() {
        CacheFacade cacheFacade = Mockito.mock(CacheFacade.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        AmqpAdmin amqpAdmin = Mockito.mock(AmqpAdmin.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);

        RouteStrategyFilter filter = new RouteStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheFacade", cacheFacade);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);
        ReflectionTestUtils.setField(filter, "amqpAdmin", amqpAdmin);
        ReflectionTestUtils.setField(filter, "rabbitTemplate", rabbitTemplate);

        StandardSubmit submit = new StandardSubmit();
        submit.setClientId(1001L);

        when(cacheFacade.getClientChannelBindings(1001L)).thenReturn(Collections.emptyList());

        try {
            filter.strategy(submit);
            Assert.fail("expected StrategyException");
        } catch (StrategyException ex) {
            Assert.assertEquals(ExceptionEnums.NO_CHANNEL.getCode(), ex.getCode());
        }

        verify(sendMsgUtil).sendWriteLog(submit);
        verify(sendMsgUtil).sendPushReport(submit);
    }

    @Test
    public void shouldKeepSameWeightBindingsAndSelectMatchingChannel() {
        CacheFacade cacheFacade = Mockito.mock(CacheFacade.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        AmqpAdmin amqpAdmin = Mockito.mock(AmqpAdmin.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);

        RouteStrategyFilter filter = new RouteStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheFacade", cacheFacade);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);
        ReflectionTestUtils.setField(filter, "amqpAdmin", amqpAdmin);
        ReflectionTestUtils.setField(filter, "rabbitTemplate", rabbitTemplate);

        StandardSubmit submit = new StandardSubmit();
        submit.setClientId(1001L);
        submit.setOperatorId(2);

        when(cacheFacade.getClientChannelBindings(1001L)).thenReturn(Arrays.asList(
                new ClientChannelBinding(2001L, 10, 0, "01"),
                new ClientChannelBinding(2002L, 10, 0, "02")
        ));
        when(cacheFacade.getChannelInfo(2001L)).thenReturn(new ChannelInfo(2001L, 0, 1, "1069"));
        when(cacheFacade.getChannelInfo(2002L)).thenReturn(new ChannelInfo(2002L, 0, 2, "1070"));

        filter.strategy(submit);

        Assert.assertEquals(Long.valueOf(2002L), submit.getChannelId());
        Assert.assertEquals("107002", submit.getSrcNumber());
        verify(rabbitTemplate).convertAndSend(RabbitMQConstants.SMS_GATEWAY + "2002", submit);
    }
}
