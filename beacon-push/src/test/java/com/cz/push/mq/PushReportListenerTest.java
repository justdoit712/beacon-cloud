package com.cz.push.mq;

import com.cz.common.model.StandardReport;
import com.cz.push.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PushReportListenerTest {

    @Test
    public void shouldAckDirectlyWhenCallbackUrlBlank() throws IOException {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        Channel channel = Mockito.mock(Channel.class);

        PushReportListener listener = buildListener(restTemplate, rabbitTemplate);
        StandardReport report = baseReport();
        report.setCallbackUrl("   ");
        Message message = buildMessage(101L);

        listener.consume(report, channel, message);

        verify(channel).basicAck(101L, false);
        verify(restTemplate, never()).postForObject(any(String.class), any(HttpEntity.class), eq(String.class));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(StandardReport.class), any(MessagePostProcessor.class));
    }

    @Test
    public void shouldAckWithoutRetryWhenPushSuccess() throws IOException {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        Channel channel = Mockito.mock(Channel.class);

        PushReportListener listener = buildListener(restTemplate, rabbitTemplate);
        StandardReport report = baseReport();
        report.setCallbackUrl(" callback.test/push ");
        Message message = buildMessage(202L);
        when(restTemplate.postForObject(eq("http://callback.test/push"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("SUCCESS");

        listener.consume(report, channel, message);

        verify(channel).basicAck(202L, false);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(StandardReport.class), any(MessagePostProcessor.class));
        Assert.assertEquals("callback.test/push", report.getCallbackUrl());
        Assert.assertEquals(Integer.valueOf(0), report.getResendCount());
    }

    @Test
    public void shouldPublishRetryWithExpectedDelayWhenPushFailed() throws IOException {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        Channel channel = Mockito.mock(Channel.class);

        PushReportListener listener = buildListener(restTemplate, rabbitTemplate);
        StandardReport report = baseReport();
        report.setCallbackUrl("callback.test/push");
        Message message = buildMessage(303L);
        when(restTemplate.postForObject(eq("http://callback.test/push"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("FAIL");

        listener.consume(report, channel, message);

        Assert.assertEquals(Integer.valueOf(1), report.getResendCount());
        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.DELAYED_EXCHANGE), eq(""), eq(report), processorCaptor.capture());
        Message retryMessage = new Message(new byte[0], new MessageProperties());
        processorCaptor.getValue().postProcessMessage(retryMessage);
        Assert.assertEquals(Integer.valueOf(15000), retryMessage.getMessageProperties().getDelay());
        verify(channel).basicAck(303L, false);
    }

    @Test
    public void shouldStopRetryWhenMaxRetryReached() throws IOException {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        Channel channel = Mockito.mock(Channel.class);

        PushReportListener listener = buildListener(restTemplate, rabbitTemplate);
        StandardReport report = baseReport();
        report.setCallbackUrl("callback.test/push");
        report.setResendCount(4);
        Message message = buildMessage(404L);
        when(restTemplate.postForObject(eq("http://callback.test/push"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("FAIL");

        listener.delayedConsume(report, channel, message);

        Assert.assertEquals(Integer.valueOf(5), report.getResendCount());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(StandardReport.class), any(MessagePostProcessor.class));
        verify(channel).basicAck(404L, false);
    }

    @Test
    public void shouldTreatJsonSerializationFailureAsRetryablePushFailure() throws IOException {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        Channel channel = Mockito.mock(Channel.class);

        PushReportListener listener = buildListener(restTemplate, rabbitTemplate);
        SelfRefReport report = new SelfRefReport();
        report.setSequenceId(90001L);
        report.setApiKey("ak_json_fail");
        report.setClientId(1009L);
        report.setMobile("13800138009");
        report.setResendCount(0);
        report.setCallbackUrl("callback.test/push");
        report.self = report;
        Message message = buildMessage(505L);

        listener.consume(report, channel, message);

        Assert.assertEquals(Integer.valueOf(1), report.getResendCount());
        verify(restTemplate, never()).postForObject(any(String.class), any(HttpEntity.class), eq(String.class));
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.DELAYED_EXCHANGE), eq(""), eq(report), any(MessagePostProcessor.class));
        verify(channel).basicAck(505L, false);
    }

    private static PushReportListener buildListener(RestTemplate restTemplate, RabbitTemplate rabbitTemplate) {
        PushReportListener listener = new PushReportListener();
        ReflectionTestUtils.setField(listener, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(listener, "rabbitTemplate", rabbitTemplate);
        return listener;
    }

    private static StandardReport baseReport() {
        StandardReport report = new StandardReport();
        report.setSequenceId(10001L);
        report.setApiKey("ak_001");
        report.setClientId(1001L);
        report.setMobile("13800138000");
        report.setResendCount(0);
        return report;
    }

    private static Message buildMessage(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }

    private static final class SelfRefReport extends StandardReport {
        public Object self;
    }
}
