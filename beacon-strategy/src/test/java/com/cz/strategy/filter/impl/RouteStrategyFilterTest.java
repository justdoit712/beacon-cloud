package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.util.ErrorSendMsgUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RouteStrategyFilterTest {

    @Test
    public void shouldSendToGatewayQueueWhenAvailableChannelFound() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        AmqpAdmin amqpAdmin = Mockito.mock(AmqpAdmin.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);

        RouteStrategyFilter filter = new RouteStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);
        ReflectionTestUtils.setField(filter, "amqpAdmin", amqpAdmin);
        ReflectionTestUtils.setField(filter, "rabbitTemplate", rabbitTemplate);

        StandardSubmit submit = new StandardSubmit();
        submit.setClientId(1001L);
        submit.setOperatorId(1);

        Set<Map> clientChannels = new LinkedHashSet<>();
        Map<String, Object> clientChannel = new HashMap<>();
        clientChannel.put("clientChannelWeight", 10);
        clientChannel.put("isAvailable", 0);
        clientChannel.put("channelId", 2001L);
        clientChannel.put("clientChannelNumber", "01");
        clientChannels.add(clientChannel);

        Map<String, Object> channel = new HashMap<>();
        channel.put("isAvailable", 0);
        channel.put("channelType", 0);
        channel.put("id", 2001L);
        channel.put("channelNumber", "1069");

        when(cacheClient.smemberMap(CacheKeyConstants.CLIENT_CHANNEL + 1001L)).thenReturn(clientChannels);
        when(cacheClient.hGetAll(CacheKeyConstants.CHANNEL + 2001L)).thenReturn(channel);

        filter.strategy(submit);

        Assert.assertEquals(Long.valueOf(2001L), submit.getChannelId());
        Assert.assertEquals("106901", submit.getSrcNumber());
        verify(amqpAdmin).declareQueue(any(Queue.class));
        verify(rabbitTemplate).convertAndSend(RabbitMQConstants.SMS_GATEWAY + "2001", submit);
    }

    @Test
    public void shouldThrowNoChannelAndSendFailureMessagesWhenNoAvailableChannel() {
        BeaconCacheClient cacheClient = Mockito.mock(BeaconCacheClient.class);
        ErrorSendMsgUtil sendMsgUtil = Mockito.mock(ErrorSendMsgUtil.class);
        AmqpAdmin amqpAdmin = Mockito.mock(AmqpAdmin.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);

        RouteStrategyFilter filter = new RouteStrategyFilter();
        ReflectionTestUtils.setField(filter, "cacheClient", cacheClient);
        ReflectionTestUtils.setField(filter, "sendMsgUtil", sendMsgUtil);
        ReflectionTestUtils.setField(filter, "amqpAdmin", amqpAdmin);
        ReflectionTestUtils.setField(filter, "rabbitTemplate", rabbitTemplate);

        StandardSubmit submit = new StandardSubmit();
        submit.setClientId(1001L);

        when(cacheClient.smemberMap(CacheKeyConstants.CLIENT_CHANNEL + 1001L))
                .thenReturn(Collections.emptySet());

        try {
            filter.strategy(submit);
            Assert.fail("expected StrategyException");
        } catch (StrategyException ex) {
            Assert.assertEquals(ExceptionEnums.NO_CHANNEL.getCode(), ex.getCode());
        }

        verify(sendMsgUtil).sendWriteLog(submit);
        verify(sendMsgUtil).sendPushReport(submit);
    }
}
