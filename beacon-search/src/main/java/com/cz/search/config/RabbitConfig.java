package com.cz.search.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.cz.common.constant.RabbitMQConstants.SMS_GATEWAY_DEAD_EXCHANGE;
import static com.cz.common.constant.RabbitMQConstants.SMS_GATEWAY_DEAD_QUEUE;
import static com.cz.common.constant.RabbitMQConstants.SMS_GATEWAY_NORMAL_EXCHANGE;
import static com.cz.common.constant.RabbitMQConstants.SMS_GATEWAY_NORMAL_QUEUE;

@Configuration
public class RabbitConfig {

    private static final int TTL = 10000;
    private static final String FANOUT_ROUTING_KEY = "";

    @Bean
    public Exchange normalExchange() {
        return ExchangeBuilder.fanoutExchange(SMS_GATEWAY_NORMAL_EXCHANGE).build();
    }

    @Bean
    public Queue normalQueue() {
        return QueueBuilder.durable(SMS_GATEWAY_NORMAL_QUEUE)
                .withArgument("x-message-ttl", TTL)
                .withArgument("x-dead-letter-exchange", SMS_GATEWAY_DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FANOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding normalBinding(Exchange normalExchange, Queue normalQueue) {
        return BindingBuilder.bind(normalQueue).to(normalExchange).with(FANOUT_ROUTING_KEY).noargs();
    }

    @Bean
    public Exchange deadExchange() {
        return ExchangeBuilder.fanoutExchange(SMS_GATEWAY_DEAD_EXCHANGE).build();
    }

    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(SMS_GATEWAY_DEAD_QUEUE).build();
    }

    @Bean
    public Binding deadBinding(Exchange deadExchange, Queue deadQueue) {
        return BindingBuilder.bind(deadQueue).to(deadExchange).with(FANOUT_ROUTING_KEY).noargs();
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
