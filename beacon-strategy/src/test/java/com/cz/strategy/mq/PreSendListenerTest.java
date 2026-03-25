package com.cz.strategy.mq;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.filter.StrategyFilterContext;
import com.rabbitmq.client.Channel;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

public class PreSendListenerTest {

    @Test
    public void shouldAckWhenStrategyPasses() throws Exception {
        StrategyFilterContext filterContext = Mockito.mock(StrategyFilterContext.class);
        Channel channel = Mockito.mock(Channel.class);

        PreSendListener listener = new PreSendListener();
        ReflectionTestUtils.setField(listener, "filterContext", filterContext);

        StandardSubmit submit = new StandardSubmit();
        Message message = buildMessage(101L);

        listener.listen(submit, message, channel);

        verify(filterContext).strategy(submit);
        verify(channel).basicAck(101L, false);
    }

    @Test
    public void shouldAckWhenStrategyThrowsBusinessException() throws Exception {
        StrategyFilterContext filterContext = Mockito.mock(StrategyFilterContext.class);
        Channel channel = Mockito.mock(Channel.class);

        PreSendListener listener = new PreSendListener();
        ReflectionTestUtils.setField(listener, "filterContext", filterContext);

        StandardSubmit submit = new StandardSubmit();
        Message message = buildMessage(202L);
        Mockito.doThrow(new StrategyException(ExceptionEnums.UNKNOWN_ERROR))
                .when(filterContext).strategy(submit);

        listener.listen(submit, message, channel);

        verify(filterContext).strategy(submit);
        verify(channel).basicAck(202L, false);
    }

    private static Message buildMessage(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }
}
