package com.cz.search.mq;

import com.cz.common.model.StandardSubmit;
import com.cz.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class SmsWriteLogListenerTest {

    @Test
    public void shouldIndexAndAckWithSendTimeMillis() throws Exception {
        SearchService searchService = Mockito.mock(SearchService.class);
        Channel channel = Mockito.mock(Channel.class);
        Message message = buildMessage(101L);

        SmsWriteLogListener listener = Mockito.spy(new SmsWriteLogListener());
        ReflectionTestUtils.setField(listener, "searchService", searchService);
        Mockito.doReturn("2026").when(listener).getYear();

        StandardSubmit submit = new StandardSubmit();
        submit.setSequenceId(123456L);
        submit.setClientId(1001L);
        submit.setSendTime(LocalDateTime.of(2026, 3, 28, 12, 30, 15));

        listener.consume(submit, channel, message);

        ArgumentCaptor<String> indexCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(searchService).index(indexCaptor.capture(), idCaptor.capture(), jsonCaptor.capture());
        Assert.assertEquals("sms_submit_log_2026", indexCaptor.getValue());
        Assert.assertEquals("123456", idCaptor.getValue());

        Map<?, ?> doc = new ObjectMapper().readValue(jsonCaptor.getValue(), Map.class);
        long expectedMillis = submit.getSendTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Assert.assertEquals(expectedMillis, ((Number) doc.get("sendTimeMillis")).longValue());

        verify(channel).basicAck(101L, false);
    }

    @Test
    public void shouldIndexAndAckWhenSendTimeMissing() throws Exception {
        SearchService searchService = Mockito.mock(SearchService.class);
        Channel channel = Mockito.mock(Channel.class);
        Message message = buildMessage(202L);

        SmsWriteLogListener listener = Mockito.spy(new SmsWriteLogListener());
        ReflectionTestUtils.setField(listener, "searchService", searchService);
        Mockito.doReturn("2026").when(listener).getYear();

        StandardSubmit submit = new StandardSubmit();
        submit.setSequenceId(777L);
        submit.setClientId(1002L);

        listener.consume(submit, channel, message);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(searchService).index(eq("sms_submit_log_2026"), eq("777"), jsonCaptor.capture());
        Map<?, ?> doc = new ObjectMapper().readValue(jsonCaptor.getValue(), Map.class);
        Assert.assertFalse(doc.containsKey("sendTimeMillis"));
        verify(channel).basicAck(202L, false);
    }

    private static Message buildMessage(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }
}
