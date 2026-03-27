package com.cz.search.mq;

import com.cz.common.model.StandardReport;
import com.cz.search.service.SearchService;
import com.cz.search.utils.SearchUtils;
import com.rabbitmq.client.Channel;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class SmsUpdateLogListenerTest {

    @Test
    public void shouldUpdateDocumentAndAck() throws Exception {
        SearchService searchService = Mockito.mock(SearchService.class);
        Channel channel = Mockito.mock(Channel.class);
        Message message = buildMessage(303L);

        SmsUpdateLogListener listener = new SmsUpdateLogListener();
        ReflectionTestUtils.setField(listener, "searchService", searchService);

        StandardReport report = new StandardReport();
        report.setSequenceId(8888L);
        report.setReportState(2);
        report.setReUpdate(false);

        listener.consume(report, channel, message);

        Assert.assertSame(report, SearchUtils.get());
        ArgumentCaptor<Map> docCaptor = ArgumentCaptor.forClass(Map.class);
        verify(searchService).update(eq(SearchUtils.INDEX + SearchUtils.getYear()), eq("8888"), docCaptor.capture());
        Assert.assertEquals(2, docCaptor.getValue().get("reportState"));
        verify(channel).basicAck(303L, false);

        SearchUtils.remove();
    }

    private static Message buildMessage(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }
}
